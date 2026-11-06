package com.flutter.gbp.betstore.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flutter.gbp.betstore.cassandra.BetRecord;
import com.flutter.gbp.betstore.cassandra.BetWriteRepository;
import com.flutter.gbp.betstore.cassandra.CassandraTestSupport;
import com.flutter.gbp.betstore.client.BetStoreClient;
import com.flutter.gbp.betstore.client.BetStoreClientConfig;
import com.flutter.gbp.betstore.client.BetStoreClientException;
import com.flutter.gbp.fcq.bet.Bet;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Contract test: the real {@link BetStoreClient} (Java 11, used by FCQ) against the real reader over HTTP.
 */
@SpringBootTest(classes = BetStoreReaderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BetStoreClientContractIT {

    private static final long ACCOUNT_ID = 5151L;
    private static final long OTHER_ACCOUNT_ID = 52L;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void cassandra(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points",
                () -> CassandraTestSupport.contactPoint().getHostString() + ":" + CassandraTestSupport.contactPoint().getPort());
        registry.add("spring.cassandra.local-datacenter", CassandraTestSupport::localDatacenter);
    }

    @BeforeEach
    void reset() {
        CassandraTestSupport.truncateAll();
    }

    @Test
    void clientReadsWhatWasWritten() {
        BetWriteRepository writer = new BetWriteRepository(CassandraTestSupport.session());
        Bet first = bet("R/CT/0001");
        Bet second = bet("R/CT/0002");
        Bet other = bet("R/CT/0003");
        writer.upsert(new BetRecord(ACCOUNT_ID, "R/CT/0001", Instant.ofEpochMilli(1), first, 1000L));
        writer.upsert(new BetRecord(ACCOUNT_ID, "R/CT/0002", Instant.ofEpochMilli(1), second, 1000L));
        writer.upsert(new BetRecord(OTHER_ACCOUNT_ID, "R/CT/0003", Instant.ofEpochMilli(1), other, 1000L));

        BetStoreClient client = new BetStoreClient(new BetStoreClientConfig(
                URI.create("http://localhost:" + port), Duration.ofSeconds(5), Duration.ofSeconds(10)));

        assertThat(client.getBets(ACCOUNT_ID).join()).containsExactly(first, second);
        assertThat(client.getBets(ACCOUNT_ID, Set.of("R/CT/0002", "R/CT/0003")).join()).containsExactly(second);
        assertThat(client.getBetsByIds(Set.of("R/CT/0003", "R/CT/0001", "missing")).join())
                .containsExactlyInAnyOrder(first, other);
        assertThat(client.getBets(1L).join()).isEmpty();
    }

    @Test
    void tooManyBetIdsIsRejectedWith400() {
        BetStoreClient client = new BetStoreClient(new BetStoreClientConfig(
                URI.create("http://localhost:" + port), Duration.ofSeconds(5), Duration.ofSeconds(10)));
        Set<String> ids = IntStream.range(0, 71).mapToObj(i -> "R/CAP/" + i).collect(Collectors.toSet());

        assertThatThrownBy(() -> client.getBetsByIds(ids).join())
                .hasCauseInstanceOf(BetStoreClientException.class)
                .satisfies(e -> assertThat(((BetStoreClientException) e.getCause()).statusCode()).isEqualTo(400));
        assertThatThrownBy(() -> client.getBets(ACCOUNT_ID, ids).join())
                .hasCauseInstanceOf(BetStoreClientException.class)
                .satisfies(e -> assertThat(((BetStoreClientException) e.getCause()).statusCode()).isEqualTo(400));
    }

    private static Bet bet(String receiptId) {
        return Bet.newBuilder().setBetReceiptId(receiptId).setCurrency("GBP").build();
    }
}
