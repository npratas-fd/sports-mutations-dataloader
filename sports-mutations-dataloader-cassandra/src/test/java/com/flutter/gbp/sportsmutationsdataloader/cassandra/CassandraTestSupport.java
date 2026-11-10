package com.flutter.gbp.sportsmutationsdataloader.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.flutter.gbp.sportsmutationsdataloader.model.SportsMutationsDataloaderTables;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.testcontainers.cassandra.CassandraContainer;

/**
 * Shared Cassandra test infrastructure: one container per JVM, with the {@code cql/sports_mutations_dataloader.cql} schema applied.
 * Published in the module's test-jar so other modules can reuse it.
 *
 * <p>Sessions are bound to their keyspace (as Boot's session is, via {@code spring.cassandra.keyspace-name}), because
 * the repositories use unqualified table names.
 */
public final class CassandraTestSupport {

    private static final String IMAGE = "cassandra:4.1";
    private static final String SCHEMA_RESOURCE = "/cql/sports_mutations_dataloader.cql";

    private static CassandraContainer container;
    private static CqlSession session;
    private static final Map<String, CqlSession> OTHER_KEYSPACE_SESSIONS = new HashMap<>();

    private CassandraTestSupport() {
    }

    /**
     * Returns a session bound to the {@value SportsMutationsDataloaderTables#KEYSPACE} keyspace on the shared container, starting it and
     * applying the schema on first use.
     */
    public static synchronized CqlSession session() {
        if (session == null) {
            container = new CassandraContainer(IMAGE);
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(CassandraTestSupport::stop));
            try (CqlSession admin = newSession(null)) {
                applySchema(admin, SportsMutationsDataloaderTables.KEYSPACE);
            }
            session = newSession(SportsMutationsDataloaderTables.KEYSPACE);
        }
        return session;
    }

    /**
     * Returns a session bound to another keyspace with the same schema, creating it on first use. Lets tests show that
     * the repositories follow the session keyspace ({@code spring.cassandra.keyspace-name}).
     */
    public static synchronized CqlSession sessionForKeyspace(String keyspace) {
        if (SportsMutationsDataloaderTables.KEYSPACE.equals(keyspace)) {
            return session();
        }
        session();
        return OTHER_KEYSPACE_SESSIONS.computeIfAbsent(keyspace, name -> {
            try (CqlSession admin = newSession(null)) {
                applySchema(admin, name);
            }
            return newSession(name);
        });
    }

    /** Removes all rows from both bet tables. */
    public static void truncateAll() {
        CqlSession s = session();
        s.execute("TRUNCATE " + SportsMutationsDataloaderTables.KEYSPACE + "." + SportsMutationsDataloaderTables.BETS);
        s.execute("TRUNCATE " + SportsMutationsDataloaderTables.KEYSPACE + "." + SportsMutationsDataloaderTables.BETS_BY_ID);
    }

    /** Contact point of the shared container, for components that build their own session. */
    public static synchronized InetSocketAddress contactPoint() {
        session();
        return container.getContactPoint();
    }

    /** Local datacenter name of the shared container. */
    public static synchronized String localDatacenter() {
        session();
        return container.getLocalDatacenter();
    }

    /** Docker id of the shared container, for tests that simulate an outage (e.g. pause/unpause). */
    public static synchronized String containerId() {
        session();
        return container.getContainerId();
    }

    private static CqlSession newSession(String keyspace) {
        var builder = CqlSession.builder()
                .addContactPoint(container.getContactPoint())
                .withLocalDatacenter(container.getLocalDatacenter());
        if (keyspace != null) {
            builder.withKeyspace(keyspace);
        }
        return builder.build();
    }

    private static synchronized void stop() {
        OTHER_KEYSPACE_SESSIONS.values().forEach(CqlSession::close);
        OTHER_KEYSPACE_SESSIONS.clear();
        if (session != null) {
            session.close();
            session = null;
        }
        if (container != null) {
            container.stop();
            container = null;
        }
    }

    /** Applies the schema script, with its keyspace name replaced by {@code keyspace}. */
    private static void applySchema(CqlSession s, String keyspace) {
        String script;
        try (InputStream in = CassandraTestSupport.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + SCHEMA_RESOURCE);
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + SCHEMA_RESOURCE, e);
        }
        Arrays.stream(stripComments(script).replace(SportsMutationsDataloaderTables.KEYSPACE + ".", keyspace + ".")
                        .replace("KEYSPACE IF NOT EXISTS " + SportsMutationsDataloaderTables.KEYSPACE + " ", "KEYSPACE IF NOT EXISTS " + keyspace + " ")
                        .split(";"))
                .map(String::strip)
                .filter(statement -> !statement.isEmpty())
                .forEach(s::execute);
    }

    private static String stripComments(String script) {
        return Arrays.stream(script.split("\n"))
                .filter(line -> !line.strip().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }
}
