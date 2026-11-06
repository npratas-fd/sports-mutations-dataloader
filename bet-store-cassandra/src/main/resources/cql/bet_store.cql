-- Keyspace for tests / local development only; in production the keyspace is provisioned by infra.
CREATE KEYSPACE IF NOT EXISTS bet_store WITH replication = {'class':'SimpleStrategy','replication_factor':1};

-- Serves reads by account (same shape as the FCQ table)
CREATE TABLE IF NOT EXISTS bet_store.bets (
    account_id           bigint,
    bet_id               text,
    bet_stream_timestamp timestamp,
    bet                  blob,
    PRIMARY KEY (account_id, bet_id)
);

-- Serves reads by betId only
CREATE TABLE IF NOT EXISTS bet_store.bets_by_id (
    bet_id               text,
    account_id           bigint,
    bet_stream_timestamp timestamp,
    bet                  blob,
    PRIMARY KEY (bet_id)
);
