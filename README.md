# sports-mutations-dataloader

A small CQRS service that keeps the **open bets** of each account and serves them over HTTP, so consumers such as
FCQ do not need to read bet data from their own Cassandra tables.

| Module | What it is |
| --- | --- |
| `sports-mutations-dataloader-writer` | Spring Boot app. Consumes the FBP (`sb_bet_processor_topic`) and ABP (`retail_bet_processor_topic`) bet topics, converts events to the FCQ `Bet` proto and writes/deletes them in Cassandra. |
| `sports-mutations-dataloader-reader` | Spring Boot app. `GET /v1/accounts/{accountId}/bets[?betIds=a,b]` (at most 70 bets, lowest bet ids first, like FCQ) and `GET /v1/bets?betIds=a,b` (at most 70 ids) (protobuf `BetList`, or proto JSON). |
| `sports-mutations-dataloader-cassandra` | Read and write repositories, `cql/sports_mutations_dataloader.cql` schema, shared Testcontainers support. |
| `sports-mutations-dataloader-model` | `BetList` proto and the blob codec. Compiled for Java 11 so Java 11 consumers (e.g. FCQ) can parse `BetList`. |

There is no client library: each consumer implements its own HTTP client against the reader's contract
(see "HTTP contract" below).

## HTTP contract

- `GET /v1/accounts/{accountId}/bets` — every open bet of the account; `?betIds=a,b` — only those ids, within the
  account. At most 70 bets are returned, lowest bet ids first (as FCQ's `ORDER BY bet_id ASC LIMIT 70`); up to 1000
  ids are accepted, more is a 400.
- `GET /v1/bets?betIds=a,b` — those ids in any account, ascending bet id order; 1 to 70 distinct ids, otherwise 400.
- `betIds`: each id URL-encoded, joined by a literal comma (ids containing a comma are not supported).
- `Accept: application/x-protobuf` (default) → `BetList` (`sports-mutations-dataloader-model`); an empty list is a
  zero-length body. `Accept: application/json` → the same message as proto JSON.
- Bets that do not exist (never seen, or already settled) are simply absent — never a 404.
- Errors are `application/problem+json`: 400 for invalid input, 503 when Cassandra is unavailable.

Design and rationale: [`docs/specs/2026-11-06-sports-mutations-dataloader-design.md`](docs/specs/2026-11-06-sports-mutations-dataloader-design.md).

## Build

Requires Java 25 and Maven 3.9+. Integration tests use Testcontainers (`cassandra:4.1`, `apache/kafka:3.9.1`), so Docker must be running.

```bash
mvn -B verify                 # build, unit tests and integration tests
mvn -B install -DskipTests    # build only
```

Notes for developers:

- **Mockito on Java 25:** Mockito cannot self-attach to the JVM. The parent `pom.xml` therefore loads `mockito-core` as a
  `-javaagent` for surefire and failsafe (`@{argLine} -javaagent:... -Xshare:off`). Do not override `argLine` in a module.
- Run a single module with its siblings: `mvn -pl sports-mutations-dataloader-writer -am verify` (siblings are not installed in `~/.m2`).
- If Testcontainers cannot find Docker, set `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` to the Docker socket.

## Run with Docker Compose

`docker-compose.yml` brings up the whole stack: Cassandra 4.1, a single-node Kafka, the two services, and two
one-shot init containers that apply `cql/sports_mutations_dataloader.cql` and create `sb_bet_processor_topic` /
`retail_bet_processor_topic` (3 partitions each). The writer and reader images copy the jars built by Maven, so build
them first.

```bash
mvn -B -DskipTests package
docker compose up --build -d
docker compose ps                                   # sports-mutations-dataloader-writer / sports-mutations-dataloader-reader become "healthy"

curl -s localhost:8080/actuator/health              # reader: cassandra UP
curl -s localhost:8081/actuator/health              # writer: cassandra + kafka UP
curl -s -H 'Accept: application/json' localhost:8080/v1/accounts/424242/bets

docker compose down                                  # add -v to also drop the Cassandra volume
```

| Service | From the host | Inside the compose network |
| --- | --- | --- |
| reader | `localhost:8080` | `sports-mutations-dataloader-reader:8080` |
| writer (actuator only) | `localhost:8081` | `sports-mutations-dataloader-writer:8080` |
| Kafka | `localhost:9094` | `kafka:9092` |
| Cassandra | `localhost:9042` (datacenter `datacenter1`) | `cassandra:9042` |

To consume the real FBP topic of the nxtbs1 dev cluster (`use1-fokfd01-nxtbs1-vme.dev.fndlsb.net:9092`, PLAINTEXT,
24 partitions, 24h retention) instead of the local broker, add the override file. The writer then uses the consumer
group `sports-mutations-dataloader-writer-local` on that cluster, starts from the earliest retained offset and has the ABP consumer
disabled:

```bash
docker compose -f docker-compose.yml -f docker-compose.nxtbs1.yml up --build -d
```

To feed the writer locally instead, publish length-delimited `BetDomainEvent` protobufs (String key, as the FBP/ABP producers do) to
`localhost:9094`; see the `send(...)` helper in `SportsMutationsDataloaderWriterIT`. The compose stack is for local development only:
replication factor 1, no auth, no TLS.

## Run locally (without Compose)

```bash
# Cassandra with the schema (the keyspace is provisioned by infra in real environments)
docker run -d --name sports-mutations-dataloader-cassandra -p 9042:9042 cassandra:4.1
docker cp sports-mutations-dataloader-cassandra/src/main/resources/cql/sports_mutations_dataloader.cql sports-mutations-dataloader-cassandra:/sports_mutations_dataloader.cql
docker exec sports-mutations-dataloader-cassandra cqlsh -f /sports_mutations_dataloader.cql   # retry until Cassandra accepts connections

# Kafka (single node) with the two topics, if you want to run the writer
docker run -d --name sports-mutations-dataloader-kafka -p 9092:9092 apache/kafka:3.9.1

mvn -B -DskipTests install
java -jar sports-mutations-dataloader-reader/target/sports-mutations-dataloader-reader-0.1.0-SNAPSHOT.jar \
  --spring.cassandra.contact-points=localhost:9042 --spring.cassandra.local-datacenter=datacenter1
java -jar sports-mutations-dataloader-writer/target/sports-mutations-dataloader-writer-0.1.0-SNAPSHOT.jar \
  --spring.cassandra.contact-points=localhost:9042 --spring.cassandra.local-datacenter=datacenter1 \
  --spring.kafka.bootstrap-servers=localhost:9092 --server.port=8081

curl -s localhost:8080/actuator/health        # {"status":"UP","components":{"cassandra":{"status":"UP"},...}}
```

## Configuration per brand / environment

Every deployment is one brand and environment; the same jar is configured with Spring properties (or the equivalent
environment variables). Defaults are in each module's `application.yml`.

**Writer**

| Property | Default | Meaning |
| --- | --- | --- |
| `spring.cassandra.contact-points`, `local-datacenter`, `keyspace-name`, credentials | keyspace `sports_mutations_dataloader` | Cassandra connection. The CQL uses unqualified table names, so `keyspace-name` selects the keyspace. |
| `spring.cassandra.request.consistency` | `local_quorum` | Consistency of every write and delete, as in FCQ. |
| `spring.kafka.bootstrap-servers` (+ security) | - | Kafka connection. See "Open before production" for SSL. |
| `spring.kafka.consumer.group-id` | `sports-mutations-dataloader-writer` | Consumer group. Offsets are committed record by record, after the Cassandra write. |
| `sports-mutations-dataloader.consumers.fbp.topic` / `.enabled` | `sb_bet_processor_topic` / `true` | FBP (online) topic and on/off switch. |
| `sports-mutations-dataloader.consumers.abp.topic` / `.enabled` | `retail_bet_processor_topic` / `true` | ABP (retail) topic and on/off switch. |
| `sports-mutations-dataloader.writer.toggles.round-start-price-half-up` | `false` | Mirrors FCQ `fcq.featureToggle.enable.roundStartPriceHalfUp`. |
| `sports-mutations-dataloader.writer.toggles.market-based-obp-bets` | `false` | Mirrors FCQ `fcq.featureToggle.enable.market.based.obp.bets`. |
| `sports-mutations-dataloader.writer.toggles.obp-bets-processing` | `false` | Mirrors FCQ `fcq.featureToggle.enable.fbp.obp.bets.processing` (FBP bets with OBP legs; ABP ignores it). |
| `sports-mutations-dataloader.writer.timestamp-field` | `CREATION_TIME` | Event field used as the Cassandra write timestamp: `CREATION_TIME` or `GENERATION_CLOCK`. |
| `server.port` | `8080` | HTTP port of the actuator endpoints (the writer has no API). |

The three conversion toggles must have **the same values as the FCQ of the same brand and environment**, otherwise the stored
bets differ from what FCQ would have produced itself. Whenever a toggle changes in FCQ it must be changed here too.

> **`timestampField=GENERATION_CLOCK` must only be enabled after the FBP/ABP team confirms that it is monotonic per bet.**
> The write timestamp decides which of two writes of the same bet wins; a non-monotonic field can let an older state
> overwrite a newer one, or let a stale insert survive a settle delete. Keep `CREATION_TIME` until then.

**Reader**

| Property | Default | Meaning |
| --- | --- | --- |
| `spring.cassandra.*` | keyspace `sports_mutations_dataloader` | Cassandra connection. The CQL uses unqualified table names, so `keyspace-name` selects the keyspace. |
| `spring.cassandra.request.consistency` | `local_quorum` | Consistency of every read, as in FCQ. With the driver default (`LOCAL_ONE`) a read could hit a replica that missed a settle delete and return a settled bet. |
| `sports-mutations-dataloader.reader.bet-limit` | `70` | Maximum bets returned by an account query, with or without `betIds`: the lowest bet ids, ascending (FCQ's `ORDER BY bet_id ASC LIMIT 70`). |
| `sports-mutations-dataloader.reader.max-bet-ids` | `70` | Maximum distinct ids in `GET /v1/bets?betIds=`; more returns `400`. |
| `sports-mutations-dataloader.reader.max-account-bet-ids` | `1000` | Maximum distinct ids in `GET /v1/accounts/{accountId}/bets?betIds=`; more returns `400`. Abuse protection only: FCQ accepts any number of ids for an account and caps the result, and so does the reader (`bet-limit`). |
| `server.max-http-request-header-size` | `64KB` | Tomcat's limit on request line + headers. Raised from 8 KB so that 1000 URL-encoded bet ids fit in the query string. |

All three values must be positive (checked at startup). Ids per endpoint, in short:

| Endpoint | Ids accepted | Bets returned |
| --- | --- | --- |
| `GET /v1/accounts/{accountId}/bets` | any number up to `max-account-bet-ids` (1000), else `400` | at most `bet-limit` (70), ascending bet id |
| `GET /v1/bets` | 1 to `max-bet-ids` (70), else `400` | all requested bets that exist, ascending bet id |

The reader answers `503` when Cassandra is unavailable.

## Operations

- **No backfill.** The service starts empty. A new consumer group starts at the `earliest` offset, which recovers whatever is
  still within the topics' retention. Open bets that predate that and receive no new event (for example long-running ante-post
  bets) are missing until their next event.
- **Cassandra availability failures stall the writer on purpose.** A write that fails with a driver error (timeouts,
  unavailable replicas, no node reachable, ...) is retried forever (exponential backoff, 0.5 s up to 30 s) and its offset is
  not committed, so no event is lost and per-partition order is kept. Watch `sports.mutations.dataloader.writer.cassandra.retries` and the
  consumer lag.
- **Permanent Cassandra failures are skipped, not retried.** A `QueryValidationException` (`InvalidQueryException`,
  `SyntaxError`, `UnauthorizedException`, `InvalidConfigurationInQueryException`, `AlreadyExistsException`) would fail the same
  way on every attempt and stop the partition for good, so the record is logged at `ERROR`, counted in
  `sports.mutations.dataloader.writer.cassandra.permanent.failures` and its offset committed: **that event is lost** for sports-mutations-dataloader and the bet
  stays as it was until its next event. Any increase of this counter needs investigation (schema, grants, keyspace).
- **Oversized batches fall back to separate writes.** An upsert writes the bet blob to both tables in one logged batch, so the
  batch is about twice the blob. Above Cassandra's `batch_size_fail_threshold` (50 KiB by default, i.e. a blob of roughly
  25 KB) Cassandra rejects it ("Batch too large"); the writer then runs the two inserts separately with the same write
  timestamp (still idempotent and ordered, but not atomic across the two tables: if the second insert fails the whole upsert
  is retried). Logged at `WARN` with the bet id and counted in `sports.mutations.dataloader.writer.cassandra.batch.fallbacks`. Batches above
  `batch_size_warn_threshold` (5 KiB by default) are accepted but logged by Cassandra.
- Records that cannot be deserialized or converted are skipped and counted (`sports.mutations.dataloader.writer.records.skipped`), not retried.
- **Health:** `/actuator/health` shows `cassandra` (both apps) and `kafka` (writer: enabled listeners running and the
  broker answers). `/actuator/prometheus` exposes the metrics.

### Metrics

| Metric | App | Tags | Notes |
| --- | --- | --- | --- |
| `kafka.consumer.fetch.manager.records.lag` | writer | `topic`, `partition`, `client.id` | Lag per partition (Kafka client metrics, with the other `kafka.consumer.*`). Published once the consumer has fetched; Micrometer binds new client metrics up to a minute later. |
| `sports.mutations.dataloader.writer.events` | writer | `source` (`fbp`/`abp`), `sourceTag` (lower-case bet source tag, `unspecified` if unknown or absent), `outcome` | Events handled: `UPSERTED`, `DELETED`, `SKIPPED_VIRTUAL`, `SKIPPED_OBP`, `SKIPPED_INVALID_ACCOUNT`, `SKIPPED_NO_TIMESTAMP`, `SKIPPED_CONVERSION_ERROR`, `SKIPPED_NO_BET`. |
| `sports.mutations.dataloader.writer.source.creation.latency` | writer | `source`, `sourceTag` | `now - CREATION_TIME`; same measurement as FCQ's `source.creation.time.latency.millis`. |
| `sports.mutations.dataloader.writer.cassandra.write` | writer | `operation` (`upsert`/`delete`), `outcome` (`success`/`failure`) | Timer of each Cassandra write, published as a histogram (p50/p95/p99 computed by the backend). |
| `sports.mutations.dataloader.writer.cassandra.retries` | writer | `topic` | Failed Cassandra writes that are being retried (availability failures). |
| `sports.mutations.dataloader.writer.cassandra.permanent.failures` | writer | `topic` | Records skipped after a permanent Cassandra failure (`QueryValidationException`); the event is lost. |
| `sports.mutations.dataloader.writer.cassandra.batch.fallbacks` | writer | `source` | Upserts whose batch was too large and were written as two separate inserts. |
| `sports.mutations.dataloader.writer.records.skipped` | writer | `topic`, `exception` | Records skipped after a non-Cassandra failure. |
| `sports.mutations.dataloader.reader.bet.ids.requested` | reader | `endpoint` (`account`/`bets`) | Distribution summary: distinct ids per request. Not recorded for an account query without `betIds`. |
| `sports.mutations.dataloader.reader.bets.returned` | reader | `endpoint` (`account`/`bets`) | Distribution summary: bets returned per request. |
| `http.server.requests` | reader | `uri`, `method`, `status`, `outcome`, `exception` | Latency histogram per endpoint (`uri`; p50/p95/p99 computed by the backend) and errors by type. |

## Open before production

These points are known and accepted for v1 but must be closed before FCQ is cut over to sports-mutations-dataloader.

1. **Write timestamp semantics must be confirmed with the FBP/ABP owners.** The write timestamp (`CREATION_TIME` by default)
   decides which of two writes of the same bet wins, but `CREATION_TIME` is not confirmed to be monotonic per bet. Two events
   of the same bet in the same millisecond tie, and on a tie Cassandra keeps the larger blob, not the later event. Consider
   `generationVersion` from the event context as ordering key or tie-breaker.
2. **Service-to-service authentication of the reader is not implemented.** `GET /v1/bets` returns the bets of any account to
   any caller, and the actuator endpoints are on the same port. Must follow the GBP service-to-service auth pattern before
   deployment.
3. **Writer throughput is sequential per partition.** Records of a partition are written one at a time (FCQ shards by key
   within a partition). Measure catch-up throughput (new consumer group from `earliest`) and settlement-burst throughput
   before cutover, and size `spring.kafka.listener.concurrency` / partitions accordingly.
4. **Datadog monitors are not shipped.** Add monitors on `sports.mutations.dataloader.writer.cassandra.retries`,
   `sports.mutations.dataloader.writer.cassandra.permanent.failures` and the consumer lag (`kafka.consumer.fetch.manager.records.lag`).
5. **Blob sizes are not measured.** Measure real bet blob sizes and agree `batch_size_warn_threshold` /
   `batch_size_fail_threshold` with infra (see "Oversized batches fall back to separate writes").
6. **Kafka SSL is not applied by the hand-built consumer factories.** The writer builds its consumer factories from
   `KafkaProperties.buildConsumerProperties()`, which does not apply SSL bundles or Boot's connection details. Configure SSL and
   SASL with plain properties: `spring.kafka.ssl.*` (keystore / truststore locations and passwords) and
   `spring.kafka.properties.*` (for example `security.protocol`, `sasl.*`).

## Service catalog

Each app has a `service.datadog.yaml`. Team, domain, vertical, platform, Slack contact and pipeline links are marked `TODO`
and must be filled in (and validated against the fd-org table) by the owning team.
