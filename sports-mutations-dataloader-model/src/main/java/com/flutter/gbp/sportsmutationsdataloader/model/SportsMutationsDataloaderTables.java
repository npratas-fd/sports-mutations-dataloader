package com.flutter.gbp.sportsmutationsdataloader.model;

/**
 * Cassandra keyspace, table and column names used by sports-mutations-dataloader.
 */
public final class SportsMutationsDataloaderTables {

    public static final String KEYSPACE = "sports_mutations_dataloader";

    /** Open bets partitioned by account. */
    public static final String BETS = "bets";
    /** Lookup table to find a bet by its id. */
    public static final String BETS_BY_ID = "bets_by_id";

    public static final String ACCOUNT_ID = "account_id";
    public static final String BET_ID = "bet_id";
    public static final String BET_STREAM_TIMESTAMP = "bet_stream_timestamp";
    public static final String BET = "bet";

    private SportsMutationsDataloaderTables() {
    }
}
