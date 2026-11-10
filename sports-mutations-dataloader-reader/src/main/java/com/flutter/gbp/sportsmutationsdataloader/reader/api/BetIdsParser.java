package com.flutter.gbp.sportsmutationsdataloader.reader.api;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parses the comma separated {@code betIds} query parameter.
 */
public final class BetIdsParser {

    private BetIdsParser() {
    }

    /**
     * Splits on commas, trims each id and drops blanks and duplicates, keeping the order of first appearance.
     *
     * @param raw raw parameter value, may be {@code null}
     * @return the distinct non-blank ids; empty if there are none
     */
    public static Set<String> parse(String raw) {
        Set<String> ids = new LinkedHashSet<>();
        if (raw == null) {
            return ids;
        }
        for (String part : raw.split(",")) {
            String id = part.strip();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }
}
