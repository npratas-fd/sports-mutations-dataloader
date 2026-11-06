package com.flutter.gbp.betstore.writer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Writer configuration ({@code bet-store.writer.*}).
 *
 * @param timestampField event context field used as the Cassandra write timestamp (default {@code CREATION_TIME})
 * @param toggles        FCQ toggles that change which bets are stored or what is stored for them
 */
@ConfigurationProperties("bet-store.writer")
public record WriterProperties(@DefaultValue("CREATION_TIME") TimestampField timestampField,
                               @DefaultValue Toggles toggles) {

    public WriterProperties {
        timestampField = timestampField == null ? TimestampField.CREATION_TIME : timestampField;
        toggles = toggles == null ? new Toggles(false, false, false) : toggles;
    }

    /**
     * Event context field that orders writes of the same bet. Values are epoch milliseconds.
     */
    public enum TimestampField {
        CREATION_TIME("CREATION_TIME"),
        GENERATION_CLOCK("generationClock");

        private final String contextKey;

        TimestampField(String contextKey) {
            this.contextKey = contextKey;
        }

        /**
         * @return the key of this field in the event context map
         */
        public String contextKey() {
            return contextKey;
        }
    }

    /**
     * Toggles taken over from FCQ. Each deployment uses the same values as the FCQ of its brand/environment.
     *
     * @param obpBetsProcessing     FCQ {@code fcq.featureToggle.enable.fbp.obp.bets.processing}: store FBP bets with
     *                              OBP legs (outcome-based legs or parts with a {@code MISC} id); ABP ignores it
     * @param roundStartPriceHalfUp FCQ {@code fcq.featureToggle.enable.roundStartPriceHalfUp}: round start prices
     *                              half-up instead of down ({@code StartPriceConverter}, {@code SgmGroupPriceConverter})
     * @param marketBasedObpBets    FCQ {@code fcq.featureToggle.enable.market.based.obp.bets}: set
     *                              {@code isMarketBasedObpLeg} on legs with a {@code MISC} part id
     *                              ({@code MarketBasedLegConverter})
     */
    public record Toggles(boolean obpBetsProcessing, boolean roundStartPriceHalfUp, boolean marketBasedObpBets) {
    }
}
