package com.flutter.gbp.betstore.writer.event;

import com.flutter.gbp.fcq.bet.Bet;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Source-neutral view of an FBP or ABP bet event: what {@link BetEventHandler} needs to apply the FCQ rules.
 *
 * @param source       {@link #FBP} or {@link #ABP}
 * @param accountIdRaw account id as received (may not be numeric)
 * @param betId        bet id
 * @param settled      the bet is settled (the stored bet must be deleted)
 * @param virtual      the bet is a virtual sports bet (never stored)
 * @param hasObpLeg    the bet has an OBP leg (outcome-based leg or a part with a {@code MISC} id)
 * @param context      event context map ({@code CREATION_TIME}, {@code generationClock}, ...)
 * @param sourceTag    metric tag for the bet source tag (lower case; {@code unspecified} when unknown or absent)
 * @param converter    converts the event to the stored bet; only called for bets that are upserted
 */
public record SourceEvent(String source, String accountIdRaw, String betId, boolean settled, boolean virtual,
                          boolean hasObpLeg, Map<String, String> context, String sourceTag, Supplier<Bet> converter) {

    /** Online bets (FBP topic). */
    public static final String FBP = "fbp";
    /** Retail bets (ABP topic). */
    public static final String ABP = "abp";
    /** Source tag value when the bet has none or it is not recognised (same naming as the FCQ metrics). */
    public static final String UNSPECIFIED_SOURCE_TAG = "unspecified";
}
