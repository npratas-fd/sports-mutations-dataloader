package com.flutter.gbp.betstore.cassandra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverTimeoutException;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/**
 * Unit test with JDK proxies as session doubles (Mockito cannot self-attach on the build JDK in the sandbox).
 */
class BetReadRepositoryTest {

    @Test
    void findByBetIdsPropagatesDriverExceptionItself() {
        BoundStatement bound = proxy(BoundStatement.class, (method, args) -> null);
        PreparedStatement prepared = proxy(PreparedStatement.class, (method, args) -> bound);
        CqlSession session = proxy(CqlSession.class, (method, args) -> switch (method.getName()) {
            case "prepare" -> prepared;
            case "executeAsync" -> CompletableFuture.<AsyncResultSet>failedFuture(new DriverTimeoutException("timed out"));
            default -> throw new UnsupportedOperationException(method.getName());
        });

        BetReadRepository repository = new BetReadRepository(session, 10);

        assertThatThrownBy(() -> repository.findByBetIds(Set.of("a")))
                .isInstanceOf(DriverTimeoutException.class)
                .isNotInstanceOf(CompletionException.class);
    }

    private interface Handler {
        Object handle(java.lang.reflect.Method method, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (p, method, args) -> handler.handle(method, args)));
    }
}
