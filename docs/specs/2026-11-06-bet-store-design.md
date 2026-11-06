# Bet Store — Documento de Design

- **Data:** 2026-11-06
- **Autor:** Nuno Pratas (com Claude)
- **Estado:** revisto (builder check feito), à espera de aprovação
- **Nome do serviço:** `bet-store` (nome provisório — *decisão minha*, trocar à vontade)

Legenda: tudo o que está marcado como *decisão minha* foi escolhido por mim (Claude) e não foi
validado pelo Nuno; o resto foi decidido por ele na conversa.

---

## 1. Intenção e porquê

Vários serviços precisam de saber o estado de uma bet (legs, odds, stake, estado, etc.). Hoje cada um
resolve isso por conta própria. O caso mais visível é o **FCQ**, que consome a `sb_bet_processor_topic`
(e a `retail_bet_processor_topic`), converte cada evento para o seu próprio modelo e guarda-o num
Cassandra privado (`fcq_bet_datastore.bets`).

A ideia é ter **um serviço dedicado** que faz esse trabalho uma vez e disponibiliza as bets via HTTP
para quem precisar. O FCQ é o primeiro cliente; outros serviços seguem-se.

Requisito central para o FCQ: **plug and play**. O FCQ deixa de ler da sua DB e passa a fazer um
pedido HTTP, **mantendo o mesmo contrato** — nada acima da camada de acesso a dados muda.

## 2. Objetivos, não-objetivos, anti-objetivos

### Objetivos
1. Consumir as streams de bets e manter, em Cassandra, as **bets abertas** de cada conta.
2. Expor uma API de leitura:
   - por **accountId** (todas as bets abertas, ou um subconjunto por `betIds`);
   - só por **lista de betIds** (sem conta).
3. Separar escrita e leitura (**CQRS**): um serviço de escrita e um serviço de leitura.
4. Permitir que o FCQ troque a sua leitura de Cassandra por HTTP sem mudar lógica de negócio.

### Não-objetivos
- Guardar histórico ou bets settled — uma bet é **apagada quando fica settled** (decisão do Nuno),
  tal como o FCQ faz hoje.
- **Backfill** de bets existentes — não é necessário (decisão do Nuno). O serviço começa vazio e
  enche-se a partir do momento em que o writer arranca.
- Expor um modelo "genérico" diferente do do FCQ na v1. O modelo exposto é o proto `Bet` do
  `fcq-bet-definition`. Um modelo mais amplo para outros consumidores fica para depois.
- Lógica de cashout, quoting, elegibilidade, enriquecimento de texto/localização. Isso continua no FCQ.
- Escrita via API. A única fonte de escrita são as topics Kafka.

### Anti-objetivos (falha mesmo que "funcione")
- O FCQ ter de mudar lógica acima do `DataStoreClient` para migrar.
- A latência de leitura degradar de forma visível o caminho de cashout quote do FCQ (que hoje lê o
  Cassandra diretamente, numa única query por pedido). Não há orçamento numérico definido por agora.
- Um evento antigo / reprocessado sobrepor um estado mais recente de uma bet.
- Uma bet settled continuar a ser servida (o FCQ ofereceria cashout numa bet já resolvida).

## 3. Restrições

- **Stack:** Java 25, Spring Boot, Cassandra (decisão do Nuno). Build com Maven (*decisão minha*,
  coerente com os outros serviços do workspace).
- **Schema Cassandra:** a tabela de bets tem de ter **a mesma estrutura que a do FCQ** (decisão do
  Nuno).
- **Contrato:** o FCQ tem de conseguir implementar a sua interface `DataStoreClient` existente sobre
  HTTP, sem alterar o tipo de retorno.
- O FCQ é um serviço Cougar (Java 11) — o cliente HTTP que for para o FCQ tem de ser compatível com
  Java 11.
- Inner-source / multi-brand: o FCQ serve UK&I e FanDuel. O serviço novo tem de ser configurável por
  brand/ambiente (topics, keyspace, toggles) da mesma forma.

## 4. O que existe hoje no FCQ (referência)

Tudo isto foi lido em `fcq-service` (master, commit `61abea27d5`).

### Tabela
`tests/component-tests/src/test/resources/fcq-infra/cassandra/cassandra-schema/init.cql`:

```sql
CREATE TABLE fcq_bet_datastore.bets (
    account_id           bigint,
    bet_id               text,
    bet_stream_timestamp timestamp,
    bet                  blob,          -- proto com.flutter.gbp.fcq.bet.Bet serializado
    PRIMARY KEY (account_id, bet_id)
);
```

### Escrita (consumer)
- `FbpEventProcessor` (`sb_bet_processor_topic`, eventos `BetDomainEvent` de
  `fbp-message-definition:1.74`) e `AbpEventProcessor` (`retail_bet_processor_topic`,
  `abp-message-definition:1.19`), ambos sobre `AbstractBetEventPersistenceProcessor`.
- Regras de filtro:
  - ignora eventos sem `Bet`;
  - ignora bets `VIRTUAL_SPORTS`;
  - FBP: bets com legs OBP (outcome-based ou market-based com feed `MISC`) só são guardadas se
    `fcq.featureToggle.enable.fbp.obp.bets.processing` estiver ligado.
- `isSettled == true` → `DELETE`; caso contrário → `INSERT` (upsert simples, **sem** guarda de ordem).
- `bet_stream_timestamp` = `context["CREATION_TIME"]` do evento.
- Conversão `BetDomainEvent → com.flutter.gbp.fcq.bet.Bet` via MapStruct em
  `service/converters/.../converters/streams/**` (~5.9k linhas de código escrito à mão).
- Consumo com sharding por chave do record Kafka (ordem garantida por chave), commit manual,
  retries via `RetryableOperationHandler`.

### Leitura
`service/datastore/.../DataStoreClient.java`:

```java
CompletableFuture<List<Bet>> getBets(long accountId);                     // todas as bets da conta
CompletableFuture<List<Bet>> getBets(long accountId, Set<String> betIds); // subconjunto
```

- As queries têm `ORDER BY bet_id ASC LIMIT <betLimit>` (`betLimit` alterável por JMX).
- Usado por `BetDataStoreDataSource` (`getAllBets`, `getBets`, `getMarketBets`), que depois filtra
  teasers, converte para o modelo de domínio e aplica enrichers — **nada disto muda**.
- A escolha entre Cassandra e FBR (fonte HTTP legada) é feita pelo toggle
  `fcq.featureToggle.enable.datastore`; o bean `betDataStoreDataSource` só existe com o profile
  `cassandra`.

## 5. Arquitetura

```
 sb_bet_processor_topic ──┐
                          ├──► bet-store-writer ──► Cassandra (bet_store)
 retail_bet_processor_topic┘         │                 ├─ bets        (account_id, bet_id)
                                     │                 └─ bets_by_id  (bet_id)
                                     │
                                     ▼
                          bet-store-reader  ◄── HTTP ── FCQ (HttpDataStoreClient)
                                            ◄── HTTP ── outros serviços
```

### Repositório e módulos (*decisão minha*)
Um único repositório Maven multi-módulo com dois deployables independentes:

| Módulo | Conteúdo |
| --- | --- |
| `bet-store-model` | dependência de `fcq-bet-definition`; constantes de tabela; serializer/deserializer do blob |
| `bet-store-cassandra` | acesso a dados partilhado: prepared statements, escrita e leitura |
| `bet-store-writer` | app Spring Boot: consumers Kafka, conversores, regras de filtro |
| `bet-store-reader` | app Spring Boot: API HTTP |
| `bet-store-client` | cliente HTTP Java 11 para consumidores (usado pelo FCQ) |

Writer e reader escalam e fazem deploy de forma independente; partilham apenas o keyspace.

## 6. Modelo de dados

Keyspace `bet_store` (nome *decisão minha*; replicação/consistência por ambiente, definida em infra).

```sql
-- Igual à tabela do FCQ: serve leituras por conta
CREATE TABLE bet_store.bets (
    account_id           bigint,
    bet_id               text,
    bet_stream_timestamp timestamp,
    bet                  blob,
    PRIMARY KEY (account_id, bet_id)
);

-- Nova: serve leituras só por betId (*decisão minha*)
CREATE TABLE bet_store.bets_by_id (
    bet_id               text,
    account_id           bigint,
    bet_stream_timestamp timestamp,
    bet                  blob,
    PRIMARY KEY (bet_id)
);
```

- `bets_by_id` duplica o blob (em vez de ser só um índice `bet_id → account_id`) para que cada
  leitura seja um único acesso ao Cassandra (*decisão minha*).
- Não usar secondary index nem materialized view em `bets` — desempenho e estabilidade fracos em
  produção (*decisão minha*).

### Escrita idempotente e ordenada (*decisão minha*)
Todas as escritas usam `USING TIMESTAMP <bet_stream_timestamp em micros>`:

```sql
INSERT INTO bet_store.bets (...) VALUES (...) USING TIMESTAMP ?;
INSERT INTO bet_store.bets_by_id (...) VALUES (...) USING TIMESTAMP ?;
DELETE FROM bet_store.bets WHERE account_id = ? AND bet_id = ? USING TIMESTAMP ?;   -- settled
DELETE FROM bet_store.bets_by_id WHERE bet_id = ? USING TIMESTAMP ?;
```

Assim, se um evento antigo chegar depois de um mais recente (reprocessamento, rebalance, replay), o
Cassandra mantém o mais recente — incluindo o caso "insert atrasado depois do delete de settle". Hoje o
FCQ faz `INSERT` sem timestamp e depende só da ordem do Kafka.

**Que campo usar como timestamp (*decisão minha*, a validar com a equipa FBP/ABP antes de implementar):**
o contexto do evento tem `CREATION_TIME` (ms), `generationClock` (ms) e `generationVersion`
(ex.: `obp-native-sgx-example.json` no FCQ). O FCQ só usa `CREATION_TIME` e nunca lê os outros dois.
Antes de escrever o writer, confirmar com os donos do FBP/ABP:
- qual destes campos é monotónico **por bet**, mesmo entre nós produtores diferentes;
- se eventos de settle e eventos ABP trazem sempre esse campo.

Por omissão: `generationClock` se for monotónico por bet; caso contrário, `CREATION_TIME`. Em ambos os
casos: `timestamp = valor_ms * 1000`. Se faltar o campo num evento de upsert → log + métrica + ignora
(paridade com o FCQ). Num delete de settle sem o campo → delete sem `USING TIMESTAMP`, para nunca
deixar uma bet settled viva.

**Limitação conhecida:** um replay do mesmo evento dá empate de timestamp e não reescreve a linha
de forma fiável. Se for preciso reconverter bets já guardadas (ex.: após corrigir um conversor), a
ferramenta de reprocessamento escreve com `timestamp + 1`. Não faz parte da v1.

As duas tabelas são escritas num **logged batch** (são partições diferentes; o logged batch garante
que ambas acabam aplicadas). *Decisão minha*; o builder pode trocar por duas escritas assíncronas com
retry se o custo do batch log for relevante.

## 7. Serviço de escrita — `bet-store-writer`

- **Consumo:** Spring Kafka, um listener por topic, cada um ligável/desligável por configuração
  (`bet-store.consumers.fbp.enabled`, `bet-store.consumers.abp.enabled`).
- **Ordem:** processamento sequencial por chave do record (concorrência por partição ou sharding por
  chave, como no FCQ). Commit manual do offset **só depois** da escrita no Cassandra ter sucesso.
- **Regras:** exatamente as do FCQ (secção 4) — ignora virtuais, OBP atrás de toggle no FBP,
  apaga em settle (*decisão minha*: paridade total com o FCQ na v1).
- **Conversão:** os conversores FBP e ABP → `com.flutter.gbp.fcq.bet.Bet` são **copiados** do FCQ
  (`service/converters/.../streams/**`, ~5.9k linhas), não extraídos para uma lib partilhada
  (*decisão minha*). Mantêm-se as mesmas versões de `fbp-message-definition`,
  `abp-message-definition` e `fcq-bet-definition` que o FCQ usa no momento da cópia.
- **Toggles que mudam o que é guardado** (*decisão minha*): os conversores copiados dependem de
  toggles do FCQ que alteram o proto guardado ou se a bet é guardada:
  `fcq.featureToggle.enable.roundStartPriceHalfUp`, o toggle de OBP market-based usado em
  `MarketBasedLegConverter` e `fcq.featureToggle.enable.fbp.obp.bets.processing`. Os valores variam por
  brand/ambiente. Regras:
  - há **um deployment do bet-store por cada brand/ambiente do FCQ** (drkpp, nxtpp, nxtfd, nxtsbg,
    drkbf, …), com os mesmos valores destes toggles que o FCQ desse ambiente;
  - a partir do corte, o bet-store passa a ser o dono destes toggles; o FCQ deixa de os usar na escrita;
  - mudar um toggle afeta só bets escritas a partir daí, tal como hoje no FCQ. Não há reconversão
    das bets já guardadas.
- **Erros:**
  - evento inválido / falha de conversão / `accountId` não numérico → log + métrica, avança o offset
    (comportamento atual do FCQ);
  - falha no Cassandra → retry com backoff; esgotados os retries, o consumer pára e não faz commit
    (não se perdem eventos). Sem DLQ na v1 (*decisão minha*).
- **Arranque sem backfill:** `auto.offset.reset=earliest` no primeiro arranque do consumer group,
  como no FCQ (*decisão minha*). Sem backfill, isto recupera pelo menos o que ainda estiver na
  retenção da topic.

## 8. Serviço de leitura — `bet-store-reader`

Spring MVC com virtual threads (Java 25) e o driver Cassandra 4.x diretamente (prepared statements,
sem Spring Data repositories) — *decisão minha*.

### Endpoints

**1. Por conta** — serve o FCQ (plug and play):

```
GET /v1/accounts/{accountId}/bets
GET /v1/accounts/{accountId}/bets?betIds=1,2,3
```

- Sem `betIds` → todas as bets abertas da conta (equivale a `getBets(accountId)`).
- Com `betIds` → só essas, dentro da conta (equivale a `getBets(accountId, betIds)`).
- Mantém a semântica atual: `ORDER BY bet_id ASC LIMIT <betLimit>`, com `betLimit` configurável.

**2. Só por betIds** — para outros consumidores:

```
GET /v1/bets?betIds=1,2,3
```

- Lê de `bets_by_id` (queries por chave em paralelo).
- Número máximo de ids por pedido configurável; acima disso → `400`.

### Formato da resposta (*decisão minha*)
Content negotiation pelo header `Accept`:

- `application/x-protobuf` — mensagem wrapper `BetList { repeated com.flutter.gbp.fcq.bet.Bet bets = 1; }`.
  O FCQ usa este formato: zero conversões, o cliente devolve diretamente `List<Bet>`.
- `application/json` — o mesmo modelo serializado com `JsonFormat` do protobuf, para quem não quiser
  depender de proto.

A wrapper `BetList` vive no módulo `bet-store-model` (novo proto, não altera o `fcq-bet-definition`).

### Semântica
- Bets inexistentes (nunca vistas ou já settled) simplesmente não aparecem na resposta; `200` com
  lista vazia se nenhuma existir. Igual ao comportamento atual do `ReadOperation`.
- `accountId` não numérico / `betIds` vazio malformado → `400`.
- Erro do Cassandra → `503`; o cliente do FCQ traduz para a mesma exceção que hoje
  (`ReadOperationException`), que o `BetDataStoreDataSource` já converte em `CougarServiceException`.
- Autenticação serviço-a-serviço: segue o padrão GBP em uso (o builder confirma qual).

## 9. Integração com o FCQ

1. **Cliente:** `bet-store-client` (Java 11) com `getBets(accountId)` e `getBets(accountId, betIds)`
   devolvendo `CompletableFuture<List<com.flutter.gbp.fcq.bet.Bet>>`.
2. **No FCQ:** nova classe `HttpDataStoreClient implements DataStoreClient` que delega no cliente.
   `deleteBet` / `persistBet` não são suportados (o FCQ deixa de escrever).
   Mantém o `join()` explicado no `ReadOperation` (o quoting tem de correr nas threads do command
   processor, não nas de I/O do cliente HTTP).
3. **Seleção:** novo toggle `fcq.featureToggle.enable.betstore.read` (nome *decisão minha*) que escolhe
   entre o `DataStoreClient` Cassandra e o HTTP. O resto da cadeia (`BetDataStoreDataSource`,
   enrichers, toggle `fcq.featureToggle.enable.datastore`) não muda.
4. **Shadow read** (*decisão minha*): numa fase intermédia, o FCQ continua a responder a partir do seu
   Cassandra e, em paralelo e fora do caminho crítico, chama o bet-store e compara (contagem e
   igualdade dos protos por `betId`). Métricas de divergência no Datadog.
5. **Corte:** ligar o toggle. Por agora, **não há um critério formal de corte nem um orçamento de
   latência** (decisão do Nuno: não é uma preocupação de momento). As métricas de divergência e
   latência do shadow read existem para informar essa decisão quando for tomada.
   Rollback = desligar o toggle (o Cassandra e consumers do FCQ continuam ativos até ao fim da migração).
6. **Desligar o antigo:** só depois de um período estável em produção — desligar os consumers
   FBP/ABP do FCQ e, mais tarde, o keyspace `fcq_bet_datastore`.

## 10. Observabilidade

- Writer: lag por partição, eventos processados/ignorados/falhados por motivo e `sourceTag`, latência
  `now - CREATION_TIME` (mesma métrica que o FCQ tem: `source.creation.time.latency.millis`), latência de
  escrita no Cassandra.
- Reader: latência p50/p95/p99 por endpoint, nº de ids pedidos vs devolvidos, erros por tipo.
- Shadow read (no FCQ): taxa de divergência e latência do bet-store.
- Health checks: Kafka e Cassandra no writer, Cassandra no reader.
- `service.datadog.yaml` conforme o baseline da FanDuel.

## 11. Testes

- Unitários para regras de filtro, conversores e mapeamento HTTP.
- **Paridade dos conversores:** copiar os testes de conversão do FCQ juntamente com o código; o mesmo
  `BetDomainEvent` tem de produzir byte-a-byte o mesmo proto que no FCQ.
- Integração com Testcontainers (Kafka + Cassandra): evento → linha nas duas tabelas → resposta HTTP;
  settle → desaparece; evento fora de ordem não sobrepõe o mais recente.
- Contrato: testes do `bet-store-client` contra o reader real (Testcontainers) para os dois métodos.
- Performance: teste de carga do endpoint por conta com o perfil de tráfego do FCQ (ver
  `fcq-service/perf_tests`).

## 12. Riscos

| Risco | Impacto | Mitigação |
| --- | --- | --- |
| **Sem backfill**: bets abertas antes do arranque do writer que não recebam novos eventos ficam em falta (ex.: ante-post de longa duração) | FCQ não oferece cashout nessas bets após o corte | Aceite por agora (decisão do Nuno). `earliest` recupera o que estiver na retenção; o shadow read mede a divergência |
| Latência extra do salto HTTP no caminho de quote | Quote mais lento | Aceite por agora, sem orçamento definido (decisão do Nuno). Protobuf, keep-alive / pool de ligações, timeouts curtos; a latência é medida no shadow read |
| Divergência dos conversores copiados face ao FCQ ao longo do tempo | Bets diferentes entre FCQ e bet-store | Testes de paridade; durante a migração qualquer alteração ao conversor no FCQ tem de ser replicada |
| Hot partitions em contas com muitas bets | Leituras lentas | Mesmo risco que o FCQ já tem hoje; `betLimit` mantém-se |
| Logged batch em duas tabelas | Custo de escrita | Medir; alternativa: escritas independentes com retry |

## 13. Deixado ao builder

- Versões exatas de Spring Boot / Spring Kafka / driver Cassandra compatíveis com Java 25.
- Estrutura de pacotes, nomes de classes, formato de configuração por ambiente/brand.
- Mecanismo de concorrência do consumer (concorrência por partição vs sharding por chave), desde que
  respeite a ordem por chave.
- Timeouts, tamanhos de pool, limites de ids por pedido (com valores iniciais medidos).
- Pipeline CI/CD, Dockerfile, infra (Ansible/K8s) seguindo o padrão DevXP.
- Padrão de autenticação serviço-a-serviço.
