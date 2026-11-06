package com.flutter.gbp.betstore.reader.config;

import static com.google.common.base.Preconditions.checkArgument;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reader configuration ({@code bet-store.reader.*}).
 *
 * @param betLimit         maximum number of bets returned by an account query, with or without {@code betIds}
 *                         (default 70); the bets with the lowest bet ids are returned, as FCQ does
 * @param maxBetIds        maximum number of distinct ids accepted by {@code GET /v1/bets?betIds=} (default 70); more
 *                         is a 400
 * @param maxAccountBetIds maximum number of distinct ids accepted by {@code GET /v1/accounts/{accountId}/bets?betIds=}
 *                         (default 1000); more is a 400. Abuse protection only: below it the result is capped by
 *                         {@code betLimit}, like FCQ, which accepts any number of ids
 */
@ConfigurationProperties("bet-store.reader")
public record ReaderProperties(@DefaultValue("70") int betLimit, @DefaultValue("70") int maxBetIds,
                               @DefaultValue("1000") int maxAccountBetIds) {

    /**
     * @throws IllegalArgumentException if any value is not positive
     */
    public ReaderProperties {
        checkArgument(betLimit > 0, "betLimit (bet-store.reader.bet-limit) must be positive: %s", betLimit);
        checkArgument(maxBetIds > 0, "maxBetIds (bet-store.reader.max-bet-ids) must be positive: %s", maxBetIds);
        checkArgument(maxAccountBetIds > 0,
                "maxAccountBetIds (bet-store.reader.max-account-bet-ids) must be positive: %s", maxAccountBetIds);
    }
}
