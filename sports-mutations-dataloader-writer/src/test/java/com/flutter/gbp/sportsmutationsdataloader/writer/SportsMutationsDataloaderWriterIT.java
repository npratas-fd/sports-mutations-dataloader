package com.flutter.gbp.sportsmutationsdataloader.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.cql.Row;
import com.flutter.gbp.sportsmutationsdataloader.cassandra.BetWriteRepository;
import com.flutter.gbp.sportsmutationsdataloader.cassandra.CassandraTestSupport;
import com.flutter.gbp.sportsmutationsdataloader.model.BetBlobCodec;
import com.flutter.gbp.fcq.bet.Bet;
import com.github.dockerjava.api.DockerClient;
import com.google.protobuf.MessageLite;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass;
import com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass.BetContext;
import com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass.BetDomainEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

/**
 * End-to-end test of the writer: Kafka (FBP / ABP topics) → listeners → event rules → Cassandra.
 *
 * <p>One Kafka container for the class and the shared Cassandra container of {@link CassandraTestSupport}. The main
 * context uses the default topic names and consumer group of {@code application.yml}.
 */
@SpringBootTest(classes = SportsMutationsDataloaderWriterApplication.class)
class SportsMutationsDataloaderWriterIT {

    private static final String FBP_TOPIC = "sb_bet_processor_topic";
    private static final String ABP_TOPIC = "retail_bet_processor_topic";
    private static final String DISABLED_FBP_TOPIC = "disabled_it_sb_bet_processor_topic";
    private static final String DISABLED_ABP_TOPIC = "disabled_it_retail_bet_processor_topic";

    private static final long ACCOUNT_ID = 12345L;
    private static final Duration STORE_TIMEOUT = Duration.ofSeconds(60);

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");
    private static KafkaProducer<String, byte[]> producer;

    static {
        KAFKA.start();
        createTopics(FBP_TOPIC, ABP_TOPIC, DISABLED_FBP_TOPIC, DISABLED_ABP_TOPIC);
    }

    @MockitoSpyBean
    private BetWriteRepository repository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private CqlSession session;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @DynamicPropertySource
    static void connectionProperties(DynamicPropertyRegistry registry) {
        connectionProperties().forEach((key, value) -> registry.add(key, () -> value));
    }

    @AfterAll
    static void closeProducer() {
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }

    @BeforeEach
    void cleanTables() {
        CassandraTestSupport.truncateAll();
    }

    /** Writes and deletes at LOCAL_QUORUM, like FCQ, so the reader's LOCAL_QUORUM reads see them. */
    @Test
    void cassandraWritesUseLocalQuorum() {
        assertThat(session.getContext().getConfig().getDefaultProfile()
                .getString(DefaultDriverOption.REQUEST_CONSISTENCY)).isEqualTo("LOCAL_QUORUM");
    }

    @Test
    void openFbpEventIsStored() {
        String betId = "O/IT/OPEN/0001";
        long creationTime = 1_785_343_197_501L;

        send(FBP_TOPIC, betId, fbpEvent(betId, creationTime, false));

        await().atMost(STORE_TIMEOUT).untilAsserted(() -> {
            Row byAccount = rowByAccount(betId).orElseThrow();
            assertThat(byAccount.getInstant("bet_stream_timestamp")).isEqualTo(Instant.ofEpochMilli(creationTime));
            assertThat(decode(byAccount).getBetReceiptId()).isEqualTo(betId);

            Row byId = rowById(betId).orElseThrow();
            assertThat(byId.getLong("account_id")).isEqualTo(ACCOUNT_ID);
            assertThat(decode(byId).getBetReceiptId()).isEqualTo(betId);
        });
    }

    @Test
    void settledFbpEventRemovesBet() {
        String betId = "O/IT/SETTLED/0001";
        long creationTime = 1_785_343_197_501L;

        send(FBP_TOPIC, betId, fbpEvent(betId, creationTime, false));
        await().atMost(STORE_TIMEOUT).until(() -> rowByAccount(betId).isPresent() && rowById(betId).isPresent());

        send(FBP_TOPIC, betId, fbpEvent(betId, creationTime + 1_000, true));
        await().atMost(STORE_TIMEOUT).until(() -> rowByAccount(betId).isEmpty() && rowById(betId).isEmpty());
    }

    @Test
    void abpEventIsStored() {
        String betId = "R/IT/OPEN/0001";
        long creationTime = 1_785_343_197_501L;

        send(ABP_TOPIC, betId, abpEvent(betId, creationTime));

        await().atMost(STORE_TIMEOUT).untilAsserted(() -> {
            assertThat(decode(rowByAccount(betId).orElseThrow()).getBetReceiptId()).isEqualTo(betId);
            assertThat(decode(rowById(betId).orElseThrow()).getBetReceiptId()).isEqualTo(betId);
        });
    }

    /**
     * A record that cannot be deserialized is logged and skipped (ErrorHandlingDeserializer): it does not block the
     * partition and its offset is committed.
     */
    @Test
    void poisonMessageIsSkipped() {
        String betId = "O/IT/AFTER-POISON/0001";
        // Declares a 10-byte delimited message but carries only 2 bytes: parseDelimitedFrom fails
        long poisonOffset = sendBytes(FBP_TOPIC, "poison", new byte[] {10, 1, 2}).offset();
        long validOffset = send(FBP_TOPIC, betId, fbpEvent(betId, 1_785_343_197_501L, false)).offset();

        await().atMost(STORE_TIMEOUT).until(() -> rowByAccount(betId).isPresent());
        assertThat(validOffset).isGreaterThan(poisonOffset);
        await().atMost(STORE_TIMEOUT).until(() -> committedOffset(groupId, FBP_TOPIC).orElse(-1) > validOffset);
    }

    /**
     * A context of its own (own topics and consumer group, closed at the end) so the consumers of the main context
     * do not interfere.
     */
    @Test
    void disabledConsumerDoesNotConsume() {
        String disabledGroupId = "sports-mutations-dataloader-writer-disabled-it";
        String abpBetId = "R/IT/DISABLED/0001";
        String fbpBetId = "O/IT/DISABLED/0001";

        Map<String, Object> properties = new HashMap<>(connectionProperties());
        properties.put("sports-mutations-dataloader.consumers.abp.enabled", "false");
        properties.put("sports-mutations-dataloader.consumers.abp.topic", DISABLED_ABP_TOPIC);
        properties.put("sports-mutations-dataloader.consumers.fbp.topic", DISABLED_FBP_TOPIC);
        properties.put("spring.kafka.consumer.group-id", disabledGroupId);
        // The second context cannot share the actuator HTTP port with the first one
        properties.put("server.port", "0");
        // Command line arguments: they take precedence over application.yml (builder "properties" would not)
        String[] args = properties.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);

        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(SportsMutationsDataloaderWriterApplication.class).run(args)) {

            List<MessageListenerContainer> containers =
                    List.copyOf(context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainers());
            assertThat(containers).filteredOn(c -> listensTo(c, DISABLED_ABP_TOPIC))
                    .noneMatch(MessageListenerContainer::isRunning);
            assertThat(containers).filteredOn(c -> listensTo(c, DISABLED_FBP_TOPIC))
                    .singleElement().matches(MessageListenerContainer::isRunning);

            send(DISABLED_ABP_TOPIC, abpBetId, abpEvent(abpBetId, 1_785_343_197_501L));
            // Positive control: the enabled FBP consumer of the same context stores its event
            send(DISABLED_FBP_TOPIC, fbpBetId, fbpEvent(fbpBetId, 1_785_343_197_501L, false));
            await().atMost(STORE_TIMEOUT).until(() -> rowByAccount(fbpBetId).isPresent());

            assertThat(rowByAccount(abpBetId)).isEmpty();
            assertThat(rowById(abpBetId)).isEmpty();
            assertThat(committedOffset(disabledGroupId, DISABLED_ABP_TOPIC)).isEmpty();
        }
    }

    /**
     * Review Focus 5: while Cassandra is down the record is retried and its offset is not committed; once Cassandra
     * is back the event is stored and the offset moves past it.
     */
    @Test
    void cassandraOutageDoesNotLoseEvents() {
        String betId = "O/IT/OUTAGE/0001";
        DockerClient docker = DockerClientFactory.instance().client();
        String cassandraContainerId = CassandraTestSupport.containerId();

        long offset;
        docker.pauseContainerCmd(cassandraContainerId).exec();
        try {
            offset = send(FBP_TOPIC, betId, fbpEvent(betId, 1_785_343_197_501L, false)).offset();

            // A redelivery only happens after the previous attempt threw: a 3rd attempt while Cassandra is paused
            // proves the write failed and was retried at least twice ...
            verify(repository, timeout(STORE_TIMEOUT.toMillis()).atLeast(3))
                    .upsert(argThat(record -> betId.equals(record.betId())));
            // ... each failed attempt is counted ...
            assertThat(meterRegistry.counter("sports.mutations.dataloader.writer.cassandra.retries", "topic", FBP_TOPIC).count())
                    .isGreaterThanOrEqualTo(2.0);
            // ... and the offset of the record was not committed
            OptionalLong committed = committedOffset(groupId, FBP_TOPIC);
            assertThat(committed.isEmpty() || committed.getAsLong() <= offset)
                    .as("committed offset %s must not be past record offset %s", committed, offset)
                    .isTrue();
        } finally {
            docker.unpauseContainerCmd(cassandraContainerId).exec();
        }

        await().atMost(Duration.ofSeconds(120)).until(() -> rowByAccount(betId).isPresent() && rowById(betId).isPresent());
        await().atMost(STORE_TIMEOUT).until(() -> committedOffset(groupId, FBP_TOPIC).orElse(-1) == offset + 1);
    }

    /**
     * Spec section 10: the Kafka client metrics, including the lag per partition, are published. The consumer
     * factories are built by the writer, so this fails if Boot's Micrometer consumer listener is not applied to them.
     * Micrometer binds a client metric once the client has registered it, and rescans for new ones every minute.
     */
    @Test
    void kafkaConsumerLagIsPublishedPerPartition() {
        String betId = "O/IT/LAG/0001";

        send(FBP_TOPIC, betId, fbpEvent(betId, 1_785_343_197_501L, false));
        await().atMost(STORE_TIMEOUT).until(() -> rowByAccount(betId).isPresent());

        await().atMost(Duration.ofSeconds(150)).untilAsserted(() ->
                assertThat(meterRegistry.find("kafka.consumer.fetch.manager.records.lag")
                        .tag("topic", FBP_TOPIC).tag("partition", "0").gauge()).isNotNull());
    }

    /** The kafka component reports the running listeners and the reachable broker, cassandra comes from Boot. */
    @Test
    void healthReportsCassandraAndKafkaUp() {
        CompositeHealthDescriptor health = (CompositeHealthDescriptor) healthEndpoint.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getComponents()).containsKeys("cassandra", "kafka");
        assertThat(health.getComponents().get("cassandra").getStatus()).isEqualTo(Status.UP);
        assertThat(health.getComponents().get("kafka").getStatus()).isEqualTo(Status.UP);
    }

    /** Every upsert and delete is timed, with the outcome as a tag. */
    @Test
    void cassandraWritesAreTimed() {
        String betId = "O/IT/TIMER/0001";
        double upserts = writeCount("upsert");
        double deletes = writeCount("delete");

        send(FBP_TOPIC, betId, fbpEvent(betId, 1_785_343_197_501L, false));
        await().atMost(STORE_TIMEOUT).until(() -> rowByAccount(betId).isPresent());
        send(FBP_TOPIC, betId, fbpEvent(betId, 1_785_343_198_501L, true));
        await().atMost(STORE_TIMEOUT).until(() -> rowByAccount(betId).isEmpty());

        assertThat(writeCount("upsert")).isEqualTo(upserts + 1);
        assertThat(writeCount("delete")).isEqualTo(deletes + 1);
    }

    // ------------------------------------------------------------------------------------------------ fixtures

    private static BetDomainEventOuterClass.BetDomainEvent fbpEvent(String betId, long creationTime, boolean settled) {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder().setName("Selection").putIds("RAMP", "1")))
                .build();
        return BetDomainEventOuterClass.BetDomainEvent.newBuilder()
                .setContext(BetDomainEventOuterClass.BetContext.newBuilder()
                        .putContext("CREATION_TIME", Long.toString(creationTime)))
                .setBet(BetOuterClass.Bet.newBuilder()
                        .setAccountId(Long.toString(ACCOUNT_ID))
                        .setBetId(betId)
                        .setBetReceiptId(betId)
                        .setIsSettled(settled)
                        .addLegs(LegOuterClass.Leg.newBuilder()
                                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)
                                .addPart(part)))
                .build();
    }

    private static BetDomainEvent abpEvent(String betId, long creationTime) {
        return BetDomainEvent.newBuilder()
                .setContext(BetContext.newBuilder().putContext("CREATION_TIME", Long.toString(creationTime)))
                .setBet(com.ppb.platform.sb.retail.proto.bet.BetOuterClass.Bet.newBuilder()
                        .setAccountId(Long.toString(ACCOUNT_ID))
                        .setBetId(betId)
                        .setBetReceiptId(betId)
                        .setChannel("otc"))
                .build();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static Map<String, Object> connectionProperties() {
        InetSocketAddress cassandra = CassandraTestSupport.contactPoint();
        return Map.of(
                "spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers(),
                "spring.cassandra.contact-points", cassandra.getHostString() + ":" + cassandra.getPort(),
                "spring.cassandra.local-datacenter", CassandraTestSupport.localDatacenter());
    }

    private static void createTopics(String... topics) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(Arrays.stream(topics).map(t -> new NewTopic(t, 1, (short) 1)).toList())
                    .all().get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Cannot create topics", e);
        }
    }

    /** Sends the event as the FBP/ABP producers do: length-delimited protobuf, String key. */
    private static RecordMetadata send(String topic, String key, MessageLite event) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            event.writeDelimitedTo(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sendBytes(topic, key, bytes.toByteArray());
    }

    private static synchronized RecordMetadata sendBytes(String topic, String key, byte[] value) {
        if (producer == null) {
            producer = new KafkaProducer<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        }
        try {
            return producer.send(new ProducerRecord<>(topic, key, value)).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Cannot send to " + topic, e);
        }
    }

    private static OptionalLong committedOffset(String group, String topic) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            Map<TopicPartition, OffsetAndMetadata> offsets =
                    admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(30, TimeUnit.SECONDS);
            OffsetAndMetadata offset = offsets.get(new TopicPartition(topic, 0));
            return offset == null ? OptionalLong.empty() : OptionalLong.of(offset.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Cannot read committed offsets of " + group, e);
        }
    }

    private double writeCount(String operation) {
        Timer timer = meterRegistry.find("sports.mutations.dataloader.writer.cassandra.write")
                .tag("operation", operation).tag("outcome", "success").timer();
        return timer == null ? 0 : timer.count();
    }

    private static boolean listensTo(MessageListenerContainer container, String topic) {
        String[] topics = container.getContainerProperties().getTopics();
        return topics != null && List.of(topics).contains(topic);
    }

    private static Optional<Row> rowByAccount(String betId) {
        return Optional.ofNullable(CassandraTestSupport.session().execute(
                "SELECT account_id, bet_stream_timestamp, bet FROM sports_mutations_dataloader.bets WHERE account_id = ? AND bet_id = ?",
                ACCOUNT_ID, betId).one());
    }

    private static Optional<Row> rowById(String betId) {
        return Optional.ofNullable(CassandraTestSupport.session().execute(
                "SELECT account_id, bet_stream_timestamp, bet FROM sports_mutations_dataloader.bets_by_id WHERE bet_id = ?", betId).one());
    }

    private static Bet decode(Row row) {
        return BetBlobCodec.deserialize(row.getByteBuffer("bet")).orElseThrow();
    }
}
