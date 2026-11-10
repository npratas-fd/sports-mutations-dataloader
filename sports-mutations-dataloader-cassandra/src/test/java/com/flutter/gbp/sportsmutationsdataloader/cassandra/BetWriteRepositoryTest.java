package com.flutter.gbp.sportsmutationsdataloader.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.flutter.gbp.fcq.bet.Bet;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the batch-too-large fallback, with JDK proxies as session doubles (this module has no Mockito).
 */
class BetWriteRepositoryTest {

    private static final long WRITE_TIMESTAMP = 1_700_000_000_000_000L;

    private final List<Object> executed = new ArrayList<>();
    private final Map<Object, String> queryOf = new IdentityHashMap<>();
    private final Map<Object, Object[]> bindArgsOf = new IdentityHashMap<>();
    private final List<String> prepared = new ArrayList<>();
    private RuntimeException batchFailure;

    @Test
    void batchTooLargeFallsBackToSeparateInsertsWithTheSameTimestamp() {
        // Message sent by Cassandra (BatchStatement.verifyBatchSize) above batch_size_fail_threshold
        batchFailure = new InvalidQueryException(null, "Batch too large");
        BetWriteRepository repository = new BetWriteRepository(session());

        BetWriteRepository.WriteMode mode = repository.upsert(record());

        assertThat(mode).isEqualTo(BetWriteRepository.WriteMode.UNBATCHED);
        assertThat(executed).hasSize(2).allMatch(statement -> statement instanceof BoundStatement);
        assertThat(executed).extracting(queryOf::get)
                .satisfiesExactly(
                        query -> assertThat(query).startsWith("INSERT INTO bets ").contains("USING TIMESTAMP ?"),
                        query -> assertThat(query).startsWith("INSERT INTO bets_by_id ").contains("USING TIMESTAMP ?"));
        for (Object statement : executed) {
            Object[] args = bindArgsOf.get(statement);
            assertThat(args[args.length - 1]).isEqualTo(WRITE_TIMESTAMP);
        }
    }

    @Test
    void otherInvalidQueryIsRethrownWithoutFallback() {
        batchFailure = new InvalidQueryException(null, "Undefined column name bet");
        BetWriteRepository repository = new BetWriteRepository(session());

        assertThatThrownBy(() -> repository.upsert(record())).isSameAs(batchFailure);
        assertThat(executed).isEmpty();
    }

    @Test
    void acceptedBatchIsLoggedBatch() {
        BetWriteRepository repository = new BetWriteRepository(session());

        assertThat(repository.upsert(record())).isEqualTo(BetWriteRepository.WriteMode.LOGGED_BATCH);
        assertThat(executed).singleElement().isInstanceOf(BatchStatement.class);
    }

    @Test
    void statementsUseUnqualifiedTableNames() {
        new BetWriteRepository(session());

        assertThat(prepared).isNotEmpty().noneMatch(query -> query.contains("sports_mutations_dataloader."));
    }

    private CqlSession session() {
        return proxy(CqlSession.class, (self, method, args) -> switch (method.getName()) {
            case "prepare" -> {
                String query = (String) args[0];
                prepared.add(query);
                yield preparedStatement(query);
            }
            case "execute" -> {
                if (args[0] instanceof BatchStatement) {
                    if (batchFailure != null) {
                        throw batchFailure;
                    }
                }
                executed.add(args[0]);
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private PreparedStatement preparedStatement(String query) {
        return proxy(PreparedStatement.class, (self, method, args) -> {
            if (!method.getName().equals("bind")) {
                throw new UnsupportedOperationException(method.getName());
            }
            BoundStatement bound = proxy(BoundStatement.class, (b, m, a) -> {
                throw new UnsupportedOperationException(m.getName());
            });
            queryOf.put(bound, query);
            bindArgsOf.put(bound, (Object[]) args[0]);
            return bound;
        });
    }

    private static BetRecord record() {
        Bet bet = Bet.newBuilder().setBetReceiptId("R/1").setCurrency("GBP").build();
        return new BetRecord(42L, "R/1", Instant.ofEpochMilli(1), bet, WRITE_TIMESTAMP);
    }

    private interface Handler {
        Object handle(Object self, Method method, Object[] args) throws Throwable;
    }

    /** Proxy whose {@code Object} methods use identity, so it can be a map key. */
    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) ->
                switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    case "toString" -> type.getSimpleName() + "@" + System.identityHashCode(p);
                    default -> handler.handle(p, method, args);
                }));
    }
}
