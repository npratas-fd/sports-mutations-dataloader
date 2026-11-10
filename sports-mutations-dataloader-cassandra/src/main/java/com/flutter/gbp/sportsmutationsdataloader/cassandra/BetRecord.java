package com.flutter.gbp.sportsmutationsdataloader.cassandra;

import com.flutter.gbp.fcq.bet.Bet;
import java.time.Instant;

/**
 * A bet to be stored, together with the write timestamp that orders it against other writes of the same bet.
 *
 * @param accountId            owner account (partition key of {@code bets})
 * @param betId                bet identifier
 * @param streamTimestamp      timestamp of the source event, stored in {@code bet_stream_timestamp}
 * @param bet                  the bet payload
 * @param writeTimestampMicros Cassandra write timestamp, in microseconds
 */
public record BetRecord(long accountId, String betId, Instant streamTimestamp, Bet bet, long writeTimestampMicros) {
}
