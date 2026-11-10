# Sports Mutations Dataloader — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o `sports-mutations-dataloader`: um writer que consome as topics de bets FBP/ABP e mantém as bets abertas em Cassandra, um reader HTTP com dois endpoints de leitura e um cliente Java 11 que o FCQ vai usar.

**Architecture:** Repositório Maven multi-módulo com dois deployables Spring Boot independentes (writer e reader) que partilham apenas o keyspace Cassandra e dois módulos de biblioteca (modelo e acesso a dados). O writer reutiliza (cópia) os conversores do FCQ para produzir exatamente o mesmo proto `com.flutter.gbp.fcq.bet.Bet`; o reader serve esse proto por HTTP (protobuf ou JSON).

**Tech Stack:** Java 25, Spring Boot 4.0.x (Spring MVC + virtual threads, Spring Kafka, Micrometer), Apache Cassandra Java driver 4.x (via auto-config do Boot, sem Spring Data repositories), Protobuf, MapStruct, JUnit 5, AssertJ, Testcontainers (Cassandra, Kafka), WireMock (cliente).

**Spec:** [`../specs/2026-11-06-sports-mutations-dataloader-design.md`](../specs/2026-11-06-sports-mutations-dataloader-design.md)

**Nota de teste:** o proto `Bet` não tem `betId` nem `accountId` no topo; nos testes de repositório, a chave é a da linha e o blob distingue-se por `betReceiptId`.

**Fora deste plano:** o teste de carga do spec (secção 11) — latência não é preocupação por agora (decisão do Nuno). A integração do lado do FCQ (`HttpDataStoreClient`, toggle `fcq.featureToggle.enable.sports.mutations.dataloader.read`, shadow read — secção 9 do spec) vive noutro repositório (Cougar, Java 11, regras próprias de PR/toggles) e terá um plano próprio depois deste. CI/CD, Dockerfile e infra ficam ao critério do builder (secção 13 do spec).

## Global Constraints

- Java 25 em todos os módulos **exceto** `sports-mutations-dataloader-client`, que compila com `--release 11` (o FCQ é Java 11).
- `sports-mutations-dataloader-client` não pode depender de Spring nem de nenhum outro módulo exceto `sports-mutations-dataloader-model`.
- Versões de contratos fixas, iguais às do FCQ: `com.flutter.gbp:fcq-bet-definition:0.32`, `com.ppb.platform.sb:fbp-message-definition:1.74`, `com.ppb.platform.sb.abp:abp-message-definition:1.19`, `com.flutter.gbp:sb-bet-context-lib:1.24` (confirmar `groupId` no `pom.xml` do FCQ).
- Repositório Maven: JFrog Artifactory com credenciais em `~/.m2/settings.xml` (igual aos outros serviços).
- `groupId` `com.flutter.gbp.sportsmutationsdataloader`; pacote raiz `com.flutter.gbp.sportsmutationsdataloader`.
- Keyspace `sports_mutations_dataloader`; tabelas `bets` (`PRIMARY KEY (account_id, bet_id)`, colunas iguais às do FCQ) e `bets_by_id` (`PRIMARY KEY (bet_id)`).
- Todas as escritas (insert e delete) nas duas tabelas vão num **logged batch**.
- Timestamps de escrita em microssegundos: `valor_ms * 1000`.
- `bet_stream_timestamp` = `context["CREATION_TIME"]` (paridade com o FCQ), independentemente do campo usado para `USING TIMESTAMP`.
- Leitura por conta: `ORDER BY bet_id ASC LIMIT <betLimit>`, `betLimit` por omissão **70** (valor atual `cbd.bets.limit` do FCQ).
- Bets em falta não aparecem na resposta; nunca `404` por bet em falta.
- Consumers Kafka: `auto.offset.reset=earliest`, commit manual só depois da escrita no Cassandra.
- Logging com SLF4J e placeholders `{}`; validação de argumentos com `Preconditions.checkArgument` (Guava), como no FCQ.

## Review Focus

1. **Evento de upsert atrasado depois do settle** (mesma bet, timestamp menor que o do delete) → a bet continua apagada. Teste em Task 2.
2. **`betIds` com espaços, duplicados ou vazios entre vírgulas** (`?betIds=1,,2, 2`) → ids normalizados (trim, sem vazios, sem duplicados); só `400` se não sobrar nenhum. Teste em Task 6.
3. **Conta com mais bets do que o `betLimit`** → devolve exatamente `betLimit` bets, ordenadas por `bet_id` ascendente, igual ao FCQ. Teste em Task 3.
4. **Settle sem campo de timestamp no contexto** → a bet é apagada na mesma (delete sem `USING TIMESTAMP`). Teste em Task 4.
5. **Cassandra indisponível no writer** → o offset não avança e o evento é reprocessado quando o Cassandra volta; nenhum evento perdido. Teste em Task 5.

---

## Estrutura de ficheiros

```
sports-mutations-dataloader/
├── pom.xml                                   # parent: módulos, BOM Spring Boot, versões de contratos
├── sports-mutations-dataloader-model/
│   ├── src/main/proto/sportsmutationsdataloader/BetList.proto
│   └── src/main/java/com/flutter/gbp/sportsmutationsdataloader/model/
│       ├── BetBlobCodec.java                 # Bet <-> ByteBuffer
│       └── SportsMutationsDataloaderTables.java               # nomes de keyspace/tabelas/colunas
├── sports-mutations-dataloader-cassandra/
│   ├── src/main/resources/cql/sports_mutations_dataloader.cql
│   └── src/main/java/com/flutter/gbp/sportsmutationsdataloader/cassandra/
│       ├── BetRecord.java                    # record de escrita
│       ├── BetWriteRepository.java
│       └── BetReadRepository.java
├── sports-mutations-dataloader-writer/
│   └── src/main/java/com/flutter/gbp/sportsmutationsdataloader/writer/
│       ├── SportsMutationsDataloaderWriterApplication.java
│       ├── convert/**                        # cópia de converters/streams/** + utils do FCQ
│       ├── convert/ConverterConfiguration.java
│       ├── event/SourceEvent.java
│       ├── event/FbpEventAdapter.java
│       ├── event/AbpEventAdapter.java
│       ├── event/BetEventHandler.java        # regras: skip / upsert / delete
│       ├── event/WriteTimestampResolver.java
│       ├── kafka/FbpDeserializer.java        # cópia do FCQ
│       ├── kafka/AbpDeserializer.java        # cópia do FCQ
│       ├── kafka/BetEventListeners.java
│       └── config/WriterProperties.java
├── sports-mutations-dataloader-reader/
│   └── src/main/java/com/flutter/gbp/sportsmutationsdataloader/reader/
│       ├── SportsMutationsDataloaderReaderApplication.java
│       ├── api/BetQueryController.java
│       ├── api/BetIdsParser.java
│       ├── api/ApiExceptionHandler.java
│       └── config/ReaderProperties.java
└── sports-mutations-dataloader-client/
    └── src/main/java/com/flutter/gbp/sportsmutationsdataloader/client/
        ├── SportsMutationsDataloaderClient.java
        ├── SportsMutationsDataloaderClientConfig.java
        └── SportsMutationsDataloaderClientException.java
```

---

### Task 1: Parent POM e módulo `sports-mutations-dataloader-model`

**Files:**
- Create: `pom.xml`, `sports-mutations-dataloader-model/pom.xml`, `.gitignore`
- Create: `sports-mutations-dataloader-model/src/main/proto/sportsmutationsdataloader/BetList.proto`
- Create: `sports-mutations-dataloader-model/src/main/java/com/flutter/gbp/sportsmutationsdataloader/model/BetBlobCodec.java`
- Create: `sports-mutations-dataloader-model/src/main/java/com/flutter/gbp/sportsmutationsdataloader/model/SportsMutationsDataloaderTables.java`
- Test: `sports-mutations-dataloader-model/src/test/java/com/flutter/gbp/sportsmutationsdataloader/model/BetBlobCodecTest.java`

**Interfaces:**
- Produces:
  - proto `com.flutter.gbp.sportsmutationsdataloader.api.BetList { repeated com.flutter.gbp.fcq.bet.Bet bets = 1; }` (`java_multiple_files = true`, `java_package = "com.flutter.gbp.sportsmutationsdataloader.api"`), importa `bet/Bet.proto` do `fcq-bet-definition`.
  - `final class BetBlobCodec { static ByteBuffer serialize(Bet bet); static Optional<Bet> deserialize(ByteBuffer bytes); }`
  - `final class SportsMutationsDataloaderTables` com constantes `KEYSPACE="sports_mutations_dataloader"`, `BETS="bets"`, `BETS_BY_ID="bets_by_id"`, `ACCOUNT_ID`, `BET_ID`, `BET_STREAM_TIMESTAMP`, `BET`.

- [ ] **Step 1: Criar o repositório e o parent POM**

`git init` em `sports-mutations-dataloader/`. Parent `pom.xml` com `<packaging>pom</packaging>`, os cinco módulos, `spring-boot-dependencies` 4.0.x importado em `dependencyManagement`, `maven.compiler.release=25`, versões de contratos das Global Constraints, `protobuf-maven-plugin` (ou `os-maven-plugin` + `protoc`) configurado em `pluginManagement`. O `fcq-bet-definition` tem de expor os `.proto` para o `import`; se o jar não os incluir, extraí-los com `maven-dependency-plugin:unpack` para `target/proto-deps`.

- [ ] **Step 2: Escrever os testes que falham**

```java
@Test void roundTripPreservesBet() {
    Bet bet = Bet.newBuilder().setBetReceiptId("O/123/456").setCurrency("GBP").setIsSettled(false).build();
    assertThat(BetBlobCodec.deserialize(BetBlobCodec.serialize(bet))).contains(bet);
}
@Test void corruptBytesYieldEmpty() {
    assertThat(BetBlobCodec.deserialize(ByteBuffer.wrap(new byte[]{(byte) 0xFF, 0x01}))).isEmpty();
}
@Test void nullYieldsEmpty() {
    assertThat(BetBlobCodec.deserialize(null)).isEmpty();
}
```

- [ ] **Step 3: Correr e confirmar que falham**

Run: `mvn -q -pl sports-mutations-dataloader-model test`
Expected: FAIL (compilação: `BetBlobCodec` não existe)

- [ ] **Step 4: Implementar `BetList.proto`, `BetBlobCodec`, `SportsMutationsDataloaderTables`**

`deserialize` faz `Bet.parseFrom(bytes.duplicate())`, apanha `InvalidProtocolBufferException`, faz log `warn` e devolve `Optional.empty()` (mesmo comportamento do `BetDeserializer` do FCQ, que filtra nulos).

- [ ] **Step 5: Correr e confirmar que passam**

Run: `mvn -q -pl sports-mutations-dataloader-model test`
Expected: PASS (3 testes)

- [ ] **Step 6: Commit**

```bash
git add pom.xml .gitignore sports-mutations-dataloader-model
git commit -m "feat: add parent pom and sports-mutations-dataloader-model with BetList proto and blob codec"
```

---

### Task 2: Schema e `BetWriteRepository`

**Files:**
- Create: `sports-mutations-dataloader-cassandra/pom.xml`
- Create: `sports-mutations-dataloader-cassandra/src/main/resources/cql/sports_mutations_dataloader.cql`
- Create: `sports-mutations-dataloader-cassandra/src/main/java/com/flutter/gbp/sportsmutationsdataloader/cassandra/BetRecord.java`
- Create: `sports-mutations-dataloader-cassandra/src/main/java/com/flutter/gbp/sportsmutationsdataloader/cassandra/BetWriteRepository.java`
- Test: `sports-mutations-dataloader-cassandra/src/test/java/com/flutter/gbp/sportsmutationsdataloader/cassandra/BetWriteRepositoryIT.java`
- Test: `sports-mutations-dataloader-cassandra/src/test/java/com/flutter/gbp/sportsmutationsdataloader/cassandra/CassandraTestSupport.java`

**Interfaces:**
- Consumes: `BetBlobCodec`, `SportsMutationsDataloaderTables` (Task 1).
- Produces:
  - `record BetRecord(long accountId, String betId, Instant streamTimestamp, Bet bet, long writeTimestampMicros)`
  - `class BetWriteRepository(CqlSession session)`:
    - `void upsert(BetRecord record)` — logged batch: `INSERT ... USING TIMESTAMP ?` em `bets` e `bets_by_id`.
    - `void delete(long accountId, String betId, OptionalLong writeTimestampMicros)` — logged batch de dois `DELETE`; com timestamp usa `USING TIMESTAMP ?`, sem timestamp não usa.
    - Síncrono; propaga `com.datastax.oss.driver.api.core.DriverException`.
  - `CassandraTestSupport`: container Testcontainers Cassandra partilhado + `CqlSession` com `sports_mutations_dataloader.cql` aplicado (reutilizado em Tasks 3, 5, 6).

- [ ] **Step 1: Escrever `sports_mutations_dataloader.cql`**

Exatamente as duas tabelas do spec (secção 6) mais `CREATE KEYSPACE IF NOT EXISTS sports_mutations_dataloader WITH replication = {'class':'SimpleStrategy','replication_factor':1};` (só para testes/local; produção é infra).

- [ ] **Step 2: Escrever os testes que falham** (`BetWriteRepositoryIT`)

- `upsertWritesBothTables` — depois de `upsert`, existe linha em `bets` (por `account_id`,`bet_id`) e em `bets_by_id` (por `bet_id`) com o mesmo blob e `bet_stream_timestamp`.
- `olderUpsertDoesNotOverwriteNewer` — upsert com ts 2000 (blob A), depois ts 1000 (blob B) → ambas as tabelas têm A.
- `deleteRemovesFromBothTables` — upsert ts 1000, delete ts 2000 → nenhuma linha.
- `lateUpsertAfterDeleteStaysDeleted` *(Review Focus 1)* — delete ts 2000, depois upsert ts 1000 → nenhuma linha.
- `deleteWithoutTimestampRemoves` — upsert ts 1000, `delete(..., OptionalLong.empty())` → nenhuma linha.

- [ ] **Step 3: Correr e confirmar que falham**

Run: `mvn -q -pl sports-mutations-dataloader-cassandra verify -Dit.test=BetWriteRepositoryIT`
Expected: FAIL (compilação)

- [ ] **Step 4: Implementar `BetRecord` e `BetWriteRepository`**

Prepared statements criados no construtor; `BatchStatement.newInstance(BatchType.LOGGED, ...)`. `checkArgument` para `betId` não vazio e `writeTimestampMicros > 0`.

- [ ] **Step 5: Correr e confirmar que passam**

Run: `mvn -q -pl sports-mutations-dataloader-cassandra verify -Dit.test=BetWriteRepositoryIT`
Expected: PASS (5 testes)

- [ ] **Step 6: Commit**

```bash
git add sports-mutations-dataloader-cassandra
git commit -m "feat: add sports_mutations_dataloader schema and write repository with timestamp-ordered writes"
```

---

### Task 3: `BetReadRepository`

**Files:**
- Create: `sports-mutations-dataloader-cassandra/src/main/java/com/flutter/gbp/sportsmutationsdataloader/cassandra/BetReadRepository.java`
- Test: `sports-mutations-dataloader-cassandra/src/test/java/com/flutter/gbp/sportsmutationsdataloader/cassandra/BetReadRepositoryIT.java`

**Interfaces:**
- Consumes: `CassandraTestSupport`, `BetWriteRepository`, `BetRecord` (Task 2); `BetBlobCodec` (Task 1).
- Produces: `class BetReadRepository(CqlSession session, int betLimit)`:
  - `List<Bet> findByAccount(long accountId)`
  - `List<Bet> findByAccountAndBetIds(long accountId, Set<String> betIds)`
  - `List<Bet> findByBetIds(Set<String> betIds)` — queries por chave a `bets_by_id` em paralelo (`executeAsync` + join de todas), ordem do resultado = ordem ascendente de `bet_id`.
  - Lê todas as páginas do result set. Blobs que não deserializam são ignorados.
  - Propaga `DriverException`.

- [ ] **Step 1: Escrever os testes que falham**

- `findByAccountReturnsAllAccountBets` — 3 bets na conta 1, 1 na conta 2 → 3 bets da conta 1.
- `findByAccountRespectsLimitAndOrder` *(Review Focus 3)* — `betLimit=2`, bets `"c","a","b"` → devolve `["a","b"]`.
- `findByAccountAndBetIdsFiltersWithinAccount` — pede `{"a","x"}` onde `x` é de outra conta → só `a`.
- `findByBetIdsAcrossAccounts` — `{"a"(conta 1), "z"(conta 2), "missing"}` → `a` e `z`.
- `corruptBlobIsSkipped` — linha com blob inválido inserida diretamente → não aparece, as outras sim.
- `emptyAccountReturnsEmptyList`.

- [ ] **Step 2: Correr e confirmar que falham**

Run: `mvn -q -pl sports-mutations-dataloader-cassandra verify -Dit.test=BetReadRepositoryIT`
Expected: FAIL (compilação)

- [ ] **Step 3: Implementar `BetReadRepository`**

Queries iguais às do `ReadOperation` do FCQ (`SELECT bet FROM sports_mutations_dataloader.bets WHERE account_id = ? [AND bet_id IN :betIds] ORDER BY bet_id ASC LIMIT <betLimit>`), com o limite embutido no prepared statement.

- [ ] **Step 4: Correr e confirmar que passam**

Run: `mvn -q -pl sports-mutations-dataloader-cassandra verify -Dit.test=BetReadRepositoryIT`
Expected: PASS (6 testes)

- [ ] **Step 5: Commit**

```bash
git add sports-mutations-dataloader-cassandra
git commit -m "feat: add bet read repository for account and bet-id lookups"
```

---

### Task 4: Writer — conversores copiados e regras de evento

**Files:**
- Create: `sports-mutations-dataloader-writer/pom.xml`
- Create: `sports-mutations-dataloader-writer/src/main/java/com/flutter/gbp/sportsmutationsdataloader/writer/convert/**` (cópia)
- Create: `sports-mutations-dataloader-writer/src/main/java/com/flutter/gbp/sportsmutationsdataloader/writer/convert/ConverterConfiguration.java`
- Create: `sports-mutations-dataloader-writer/src/main/java/com/flutter/gbp/sportsmutationsdataloader/writer/config/WriterProperties.java`
- Create: `sports-mutations-dataloader-writer/src/main/java/com/flutter/gbp/sportsmutationsdataloader/writer/event/{SourceEvent,FbpEventAdapter,AbpEventAdapter,BetEventHandler,WriteTimestampResolver}.java`
- Test: `sports-mutations-dataloader-writer/src/test/java/com/flutter/gbp/sportsmutationsdataloader/writer/convert/**` (cópia dos testes)
- Test: `sports-mutations-dataloader-writer/src/test/java/com/flutter/gbp/sportsmutationsdataloader/writer/event/{BetEventHandlerTest,WriteTimestampResolverTest,FbpEventAdapterTest,AbpEventAdapterTest}.java`

**Interfaces:**
- Consumes: `BetWriteRepository.upsert/delete`, `BetRecord` (Task 2).
- Produces:
  - `@ConfigurationProperties("sports-mutations-dataloader.writer") record WriterProperties(TimestampField timestampField, Toggles toggles)` com `enum TimestampField { CREATION_TIME, GENERATION_CLOCK }` (omissão `CREATION_TIME`) e `record Toggles(boolean obpBetsProcessing, boolean roundStartPriceHalfUp, boolean marketBasedObp)` (nomes finais dos dois últimos: os que o FCQ usa em `StartPriceConverter`/`MarketBasedLegConverter`).
  - `record SourceEvent(String source, String accountIdRaw, String betId, boolean settled, boolean virtual, boolean hasObpLeg, Map<String,String> context, String sourceTag, Supplier<Bet> converter)`
  - `FbpEventAdapter.adapt(BetDomainEvent) -> Optional<SourceEvent>` (vazio se `!hasBet()`); idem `AbpEventAdapter` para o evento retail.
  - `WriteTimestampResolver.resolveMicros(Map<String,String> context) -> OptionalLong`
  - `BetEventHandler.handle(SourceEvent) -> Outcome` com `enum Outcome { UPSERTED, DELETED, SKIPPED_VIRTUAL, SKIPPED_OBP, SKIPPED_INVALID_ACCOUNT, SKIPPED_NO_TIMESTAMP, SKIPPED_CONVERSION_ERROR }`; `DriverException` do repositório **propaga**.

- [ ] **Step 1: Copiar os conversores e os testes do FCQ**

Copiar de `fcq-service/service/converters/src/main/java/com/betfair/site/services/fcq/converters/`: `streams/**`, `Converter.java` e `utils/{JavaValueToProtoValueConverter,SgmGroupPriceConverter,AbstractStartPriceConverter,BetContextDecoder}.java` (e o que estes importarem), para `com.flutter.gbp.sportsmutationsdataloader.writer.convert`. Copiar os 45 testes de `src/test/**/streams/**` e os recursos de teste que referenciam. Ajustar só os `package`/`import`.

- [ ] **Step 2: Remover a dependência do `FeatureToggle` do FCQ**

Nos três ficheiros que o usam (`abp/leg/StartPriceConverter`, `fbp/leg/StartPriceConverter`, `fbp/leg/MarketBasedLegConverter`) trocar `com.ppb.platform.sb.toggle.feature.FeatureToggle` por `java.util.function.BooleanSupplier` (`isEnabled()` → `getAsBoolean()`). Nos testes, `() -> true` / `() -> false`.

- [ ] **Step 3: Escrever `ConverterConfiguration`**

`@Configuration` que reproduz 1:1 os beans de `streams` de `fcq-service/service/application/src/main/resources/conf/service-converters.xml` (os ~47 beans), injetando os `BooleanSupplier` a partir de `WriterProperties.toggles()`.

- [ ] **Step 4: Correr os testes copiados**

Run: `mvn -q -pl sports-mutations-dataloader-writer test -Dtest='com.flutter.gbp.sportsmutationsdataloader.writer.convert.**'`
Expected: PASS — o mesmo número de testes que passam no FCQ para estas classes. Se algum falhar, é desvio da cópia: corrigir a cópia, nunca o teste.

- [ ] **Step 5: Escrever os testes de regras que falham**

`WriteTimestampResolverTest`:
- `creationTimeConvertedToMicros` — `CREATION_TIME="1785343197501"` → `1785343197501000`.
- `generationClockUsedWhenConfigured` — `timestampField=GENERATION_CLOCK`, `generationClock="1785343197635"` → `1785343197635000`.
- `missingOrNonNumericYieldsEmpty`.

`BetEventHandlerTest` (repositório mockado):
- `virtualIsSkipped` → `SKIPPED_VIRTUAL`, sem chamadas ao repositório.
- `obpSkippedWhenToggleOffForFbp` / `obpProcessedWhenToggleOn`; ABP ignora o toggle (paridade com `AbpEventProcessor`).
- `nonNumericAccountIsSkipped` → `SKIPPED_INVALID_ACCOUNT`.
- `openBetIsUpsertedWithResolvedTimestampAndCreationTimeColumn` — verifica `BetRecord(accountId, betId, Instant.ofEpochMilli(CREATION_TIME), bet, micros)`.
- `upsertWithoutTimestampIsSkipped` → `SKIPPED_NO_TIMESTAMP`.
- `settledIsDeletedWithTimestamp`.
- `settledWithoutTimestampIsDeletedAnyway` *(Review Focus 4)* → `delete(..., OptionalLong.empty())`, `DELETED`.
- `conversionErrorIsSkipped` — `converter` lança → `SKIPPED_CONVERSION_ERROR`.
- `repositoryFailurePropagates` — `upsert` lança `DriverTimeoutException` → a exceção sai do `handle`.

`FbpEventAdapterTest` / `AbpEventAdapterTest`: evento sem `Bet` → vazio; `VIRTUAL_SPORTS` → `virtual=true`; leg `OUTCOME_BASED` ou part com id `MISC` → `hasObpLeg=true` (lógica igual a `FbpEventProcessor.hasAnyObpLeg`).

- [ ] **Step 6: Correr e confirmar que falham**

Run: `mvn -q -pl sports-mutations-dataloader-writer test -Dtest='com.flutter.gbp.sportsmutationsdataloader.writer.event.*Test'`
Expected: FAIL (compilação)

- [ ] **Step 7: Implementar `WriterProperties`, `SourceEvent`, adapters, `WriteTimestampResolver`, `BetEventHandler`**

O `BetEventHandler` regista um contador Micrometer `sports.mutations.dataloader.writer.events{source,outcome}` e um timer `sports.mutations.dataloader.writer.source.creation.latency{source,sourceTag}` (`now - CREATION_TIME`), como as métricas do FCQ.

- [ ] **Step 8: Correr e confirmar que passam**

Run: `mvn -q -pl sports-mutations-dataloader-writer test`
Expected: PASS (conversores copiados + regras)

- [ ] **Step 9: Commit**

```bash
git add sports-mutations-dataloader-writer
git commit -m "feat: add writer converters copied from FCQ and bet event rules"
```

---

### Task 5: Writer — consumers Kafka e aplicação

**Files:**
- Create: `sports-mutations-dataloader-writer/src/main/java/com/flutter/gbp/sportsmutationsdataloader/writer/SportsMutationsDataloaderWriterApplication.java`
- Create: `sports-mutations-dataloader-writer/src/main/java/com/flutter/gbp/sportsmutationsdataloader/writer/kafka/{FbpDeserializer,AbpDeserializer,BetEventListeners,KafkaConfiguration}.java`
- Create: `sports-mutations-dataloader-writer/src/main/resources/application.yml`
- Test: `sports-mutations-dataloader-writer/src/test/java/com/flutter/gbp/sportsmutationsdataloader/writer/SportsMutationsDataloaderWriterIT.java`

**Interfaces:**
- Consumes: `FbpEventAdapter`, `AbpEventAdapter`, `BetEventHandler` (Task 4); `BetWriteRepository` (Task 2); `CassandraTestSupport` (Task 2).
- Produces: aplicação executável. Propriedades:
  - `sports-mutations-dataloader.consumers.fbp.enabled` / `.topic` (omissão `sb_bet_processor_topic`)
  - `sports-mutations-dataloader.consumers.abp.enabled` / `.topic` (omissão `retail_bet_processor_topic`)
  - `spring.kafka.consumer.auto-offset-reset=earliest`, `enable-auto-commit=false`, `ack-mode=RECORD` (commit por record depois do `handle`)

- [ ] **Step 1: Copiar `FbpDeserializer` e `AbpDeserializer` do FCQ** (`kafka-consumers/.../fbp/deserializer`, `.../abp/deserializer`), mudando só o pacote.

- [ ] **Step 2: Escrever os testes de integração que falham** (Testcontainers Kafka + Cassandra, `@SpringBootTest`)

- `openFbpEventIsStored` — publica `BetDomainEvent` aberto → aparece em `bets` e `bets_by_id`.
- `settledFbpEventRemovesBet` — publica aberto e depois settled (timestamp maior) → desaparece.
- `abpEventIsStored`.
- `disabledConsumerDoesNotConsume` — com `sports-mutations-dataloader.consumers.abp.enabled=false`, nenhum listener container para a topic ABP.
- `cassandraOutageDoesNotLoseEvents` *(Review Focus 5)* — pausar o container Cassandra, publicar evento, retomar → o evento acaba guardado; o offset committed não avançou enquanto o Cassandra esteve em baixo.

- [ ] **Step 3: Correr e confirmar que falham**

Run: `mvn -q -pl sports-mutations-dataloader-writer verify -Dit.test=SportsMutationsDataloaderWriterIT`
Expected: FAIL

- [ ] **Step 4: Implementar `BetEventListeners` e `KafkaConfiguration`**

Um `@KafkaListener` por topic com `autoStartup` ligado à propriedade `enabled` respetiva; cada listener adapta → `handle`. Ordem por partição (concorrência do container ≤ nº de partições; sem processamento paralelo dentro da partição). Error handler: `DefaultErrorHandler` com `ExponentialBackOff` (inicial 500 ms, máx 30 s) e **sem limite de tentativas** para `DriverException` — o consumer fica em retry sem commit até o Cassandra voltar; exceções de desserialização vão para log + skip (`ErrorHandlingDeserializer`).

- [ ] **Step 5: Correr e confirmar que passam**

Run: `mvn -q -pl sports-mutations-dataloader-writer verify -Dit.test=SportsMutationsDataloaderWriterIT`
Expected: PASS (5 testes)

- [ ] **Step 6: Commit**

```bash
git add sports-mutations-dataloader-writer
git commit -m "feat: wire FBP and ABP Kafka consumers into the writer"
```

---

### Task 6: Reader — API HTTP

**Files:**
- Create: `sports-mutations-dataloader-reader/pom.xml`
- Create: `sports-mutations-dataloader-reader/src/main/java/com/flutter/gbp/sportsmutationsdataloader/reader/SportsMutationsDataloaderReaderApplication.java`
- Create: `sports-mutations-dataloader-reader/src/main/java/com/flutter/gbp/sportsmutationsdataloader/reader/api/{BetQueryController,BetIdsParser,ApiExceptionHandler}.java`
- Create: `sports-mutations-dataloader-reader/src/main/java/com/flutter/gbp/sportsmutationsdataloader/reader/config/ReaderProperties.java`
- Create: `sports-mutations-dataloader-reader/src/main/resources/application.yml`
- Test: `sports-mutations-dataloader-reader/src/test/java/com/flutter/gbp/sportsmutationsdataloader/reader/api/{BetQueryControllerTest,BetIdsParserTest}.java`
- Test: `sports-mutations-dataloader-reader/src/test/java/com/flutter/gbp/sportsmutationsdataloader/reader/SportsMutationsDataloaderReaderIT.java`

**Interfaces:**
- Consumes: `BetReadRepository` (Task 3); `BetList` (Task 1); `CassandraTestSupport`, `BetWriteRepository` (Task 2, só em testes).
- Produces:
  - `GET /v1/accounts/{accountId}/bets[?betIds=...]` e `GET /v1/bets?betIds=...`, ambos devolvem `BetList` em `application/x-protobuf` (omissão) ou `application/json` (via `ProtobufHttpMessageConverter` + `protobuf-java-util`).
  - `@ConfigurationProperties("sports-mutations-dataloader.reader") record ReaderProperties(int betLimit /*70*/, int maxBetIds /*70*/)`
  - `BetIdsParser.parse(String raw) -> Set<String>` (`LinkedHashSet`; trim, sem vazios, sem duplicados).
  - `spring.threads.virtual.enabled=true`.

- [ ] **Step 1: Escrever os testes que falham**

`BetIdsParserTest`:
- `normalizesInput` *(Review Focus 2)* — `"1,,2, 2 "` → `{"1","2"}`.
- `blankYieldsEmpty` — `" , "` → `{}`.

`BetQueryControllerTest` (`@WebMvcTest`, repositório mockado):
- `accountWithoutBetIdsCallsFindByAccount` — `200`, corpo protobuf com as bets.
- `accountWithBetIdsCallsFindByAccountAndBetIds`.
- `accountWithBlankBetIdsBehavesAsNoFilter` — `?betIds=` → `findByAccount`.
- `nonNumericAccountIdIs400`.
- `betIdsEndpointCallsFindByBetIds`.
- `betIdsEndpointWithoutIdsIs400`.
- `betIdsAboveMaxIs400` — `maxBetIds+1` ids → `400`.
- `jsonWhenAcceptJson` — `Accept: application/json` → JSON com `"bets"`.
- `unknownBetsYieldEmptyList` — repositório devolve `[]` → `200`, `BetList` vazio.
- `cassandraFailureIs503` — repositório lança `DriverTimeoutException` → `503`.

`SportsMutationsDataloaderReaderIT` (Testcontainers Cassandra + `RANDOM_PORT`): escreve 2 bets via `BetWriteRepository`, lê pelos dois endpoints e confirma que os protos devolvidos são iguais aos escritos.

- [ ] **Step 2: Correr e confirmar que falham**

Run: `mvn -q -pl sports-mutations-dataloader-reader verify`
Expected: FAIL (compilação)

- [ ] **Step 3: Implementar `ReaderProperties`, `BetIdsParser`, `BetQueryController`, `ApiExceptionHandler`**

`ApiExceptionHandler` mapeia `DriverException` → `503` e erros de validação/conversão de path → `400`, com corpo `ProblemDetail`.

- [ ] **Step 4: Correr e confirmar que passam**

Run: `mvn -q -pl sports-mutations-dataloader-reader verify`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add sports-mutations-dataloader-reader
git commit -m "feat: add reader HTTP API for account and bet-id lookups"
```

---

### Task 7: `sports-mutations-dataloader-client` (Java 11) e teste de contrato

**Files:**
- Create: `sports-mutations-dataloader-client/pom.xml` (`maven.compiler.release=11`)
- Create: `sports-mutations-dataloader-client/src/main/java/com/flutter/gbp/sportsmutationsdataloader/client/{SportsMutationsDataloaderClient,SportsMutationsDataloaderClientConfig,SportsMutationsDataloaderClientException}.java`
- Test: `sports-mutations-dataloader-client/src/test/java/com/flutter/gbp/sportsmutationsdataloader/client/SportsMutationsDataloaderClientTest.java`
- Test: `sports-mutations-dataloader-reader/src/test/java/com/flutter/gbp/sportsmutationsdataloader/reader/SportsMutationsDataloaderClientContractIT.java`

**Interfaces:**
- Consumes: `BetList` (Task 1); endpoints de Task 6.
- Produces (é isto que o `HttpDataStoreClient` do FCQ vai usar):
  - `final class SportsMutationsDataloaderClientConfig(URI baseUri, Duration connectTimeout, Duration requestTimeout)`
  - `class SportsMutationsDataloaderClient(SportsMutationsDataloaderClientConfig config)`:
    - `CompletableFuture<List<Bet>> getBets(long accountId)`
    - `CompletableFuture<List<Bet>> getBets(long accountId, Set<String> betIds)`
    - `CompletableFuture<List<Bet>> getBetsByIds(Set<String> betIds)`
  - `SportsMutationsDataloaderClientException extends RuntimeException` com `int statusCode()` (`-1` para erros de I/O/timeout).
  - `java.net.http.HttpClient` com `HTTP_1_1` keep-alive, `Accept: application/x-protobuf`, `betIds` URL-encoded e separados por vírgula. Conjunto `betIds` vazio → `CompletableFuture.completedFuture(List.of())` sem chamada HTTP (como o `BetDataStoreDataSource`).

- [ ] **Step 1: Escrever os testes que falham**

`SportsMutationsDataloaderClientTest` (WireMock):
- `getBetsByAccountParsesProtobuf` — stub `GET /v1/accounts/42/bets` devolve `BetList` com 2 bets → lista com as 2.
- `getBetsWithIdsSendsEncodedIds` — pedido contém `betIds=a%2Cb` ou `a,b` (ordem indiferente).
- `emptyBetIdsShortCircuits` — nenhum pedido feito, lista vazia.
- `non2xxCompletesExceptionallyWithStatus` — `503` → `SportsMutationsDataloaderClientException.statusCode()==503`.
- `timeoutCompletesExceptionally` — atraso > `requestTimeout` → `statusCode()==-1`.

`SportsMutationsDataloaderClientContractIT` (no reader, `RANDOM_PORT` + Cassandra): grava bets, usa o `SportsMutationsDataloaderClient` real contra o reader para os três métodos e compara com o que foi gravado.

- [ ] **Step 2: Correr e confirmar que falham**

Run: `mvn -q -pl sports-mutations-dataloader-client,sports-mutations-dataloader-reader -am verify`
Expected: FAIL (compilação)

- [ ] **Step 3: Implementar o cliente**

- [ ] **Step 4: Correr e confirmar que passam**

Run: `mvn -q -pl sports-mutations-dataloader-client,sports-mutations-dataloader-reader -am verify`
Expected: PASS

- [ ] **Step 5: Confirmar o target Java 11**

Run: `javap -v -cp sports-mutations-dataloader-client/target/classes com.flutter.gbp.sportsmutationsdataloader.client.SportsMutationsDataloaderClient | grep "major version"`
Expected: `major version: 55`

- [ ] **Step 6: Commit**

```bash
git add sports-mutations-dataloader-client sports-mutations-dataloader-reader
git commit -m "feat: add Java 11 sports-mutations-dataloader client with contract test against reader"
```

---

### Task 8: Observabilidade e documentação

**Files:**
- Create: `sports-mutations-dataloader-writer/service.datadog.yaml`, `sports-mutations-dataloader-reader/service.datadog.yaml`
- Create: `README.md`
- Modify: `sports-mutations-dataloader-writer/src/main/resources/application.yml`, `sports-mutations-dataloader-reader/src/main/resources/application.yml`

**Interfaces:**
- Consumes: métricas de Tasks 4–6.

- [ ] **Step 1: Health e métricas**

Ativar `management.endpoints.web.exposure.include=health,prometheus` nos dois apps; health de Cassandra (ambos) e Kafka (writer). Timer `sports.mutations.dataloader.writer.cassandra.write` à volta de `upsert`/`delete`; métricas HTTP do reader por endpoint (`http.server.requests` já dá p50/p95/p99 com percentis ativados).

- [ ] **Step 2: Verificar**

Run: `mvn -q verify` e arrancar o reader localmente contra o Cassandra de teste; `curl -s localhost:8080/actuator/health`
Expected: `{"status":"UP",...}` com componente `cassandra`.

- [ ] **Step 3: `service.datadog.yaml` dos dois apps**

Validar com a skill `anthropic-skills:datadog-service-catalog-validator` (baseline v2.2).

- [ ] **Step 4: README**

Como construir, correr localmente (Cassandra + Kafka via Testcontainers ou docker), propriedades por brand/ambiente (topics, toggles de conversão com os valores atuais por ambiente do FCQ, `timestampField`), e a nota de que `timestampField=GENERATION_CLOCK` só se ativa depois de a equipa FBP/ABP confirmar que é monotónico por bet.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "docs: add README, datadog service definitions and health/metrics config"
```
