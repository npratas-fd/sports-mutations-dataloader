package com.flutter.gbp.betstore.reader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reader configuration ({@code bet-store.reader.*}).
 *
 * @param betLimit  maximum number of bets returned by an account query (default 70)
 * @param maxBetIds maximum number of distinct ids accepted in {@code ?betIds=} (default 70)
 */
@ConfigurationProperties("bet-store.reader")
public record ReaderProperties(@DefaultValue("70") int betLimit, @DefaultValue("70") int maxBetIds) {
}
