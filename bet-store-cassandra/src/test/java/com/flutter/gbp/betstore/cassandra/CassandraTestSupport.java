package com.flutter.gbp.betstore.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.flutter.gbp.betstore.model.BetStoreTables;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.testcontainers.cassandra.CassandraContainer;

/**
 * Shared Cassandra test infrastructure: one container per JVM, with the {@code cql/bet_store.cql} schema applied.
 * Published in the module's test-jar so other modules can reuse it.
 */
public final class CassandraTestSupport {

    private static final String IMAGE = "cassandra:4.1";
    private static final String SCHEMA_RESOURCE = "/cql/bet_store.cql";

    private static CassandraContainer container;
    private static CqlSession session;

    private CassandraTestSupport() {
    }

    /** Returns a session on the shared container, starting it and applying the schema on first use. */
    public static synchronized CqlSession session() {
        if (session == null) {
            container = new CassandraContainer(IMAGE);
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(CassandraTestSupport::stop));
            CqlSession created = CqlSession.builder()
                    .addContactPoint(container.getContactPoint())
                    .withLocalDatacenter(container.getLocalDatacenter())
                    .build();
            applySchema(created);
            session = created;
        }
        return session;
    }

    /** Removes all rows from both bet tables. */
    public static void truncateAll() {
        CqlSession s = session();
        s.execute("TRUNCATE " + BetStoreTables.KEYSPACE + "." + BetStoreTables.BETS);
        s.execute("TRUNCATE " + BetStoreTables.KEYSPACE + "." + BetStoreTables.BETS_BY_ID);
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

    private static synchronized void stop() {
        if (session != null) {
            session.close();
            session = null;
        }
        if (container != null) {
            container.stop();
            container = null;
        }
    }

    private static void applySchema(CqlSession s) {
        String script;
        try (InputStream in = CassandraTestSupport.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + SCHEMA_RESOURCE);
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + SCHEMA_RESOURCE, e);
        }
        Arrays.stream(stripComments(script).split(";"))
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
