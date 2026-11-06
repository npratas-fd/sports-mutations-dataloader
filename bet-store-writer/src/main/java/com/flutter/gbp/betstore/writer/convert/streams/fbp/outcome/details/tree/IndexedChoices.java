package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Immutable snapshot of a validated choice tree produced by {@link ChoiceTreeIndexer} and consumed by
 * {@link ChoiceTreeRules} and {@link ChoiceTreeValidator}.
 */
final class IndexedChoices {

    private final Map<Integer, Choice> choicesByNumber;
    private final Set<Integer> rootChoiceNumbers;
    private final Map<Integer, Integer> parentChoiceNumberByChoiceNumber;
    private final List<ChoiceReference> references;
    private final String legEventRampId;
    private final int totalChoices;

    IndexedChoices(Map<Integer, Choice> choicesByNumber,
                   Set<Integer> rootChoiceNumbers,
                   Map<Integer, Integer> parentChoiceNumberByChoiceNumber,
                   List<ChoiceReference> references,
                   String legEventRampId,
                   int totalChoices) {
        checkArgument(choicesByNumber != null, "choicesByNumber must not be null");
        checkArgument(rootChoiceNumbers != null, "rootChoiceNumbers must not be null");
        checkArgument(parentChoiceNumberByChoiceNumber != null, "parentChoiceNumberByChoiceNumber must not be null");
        checkArgument(references != null, "references must not be null");
        checkArgument(StringUtils.isNotBlank(legEventRampId), "legEventRampId must not be blank");
        checkArgument(totalChoices >= 0, "totalChoices must be >= 0");
        this.choicesByNumber = Collections.unmodifiableMap(choicesByNumber);
        this.rootChoiceNumbers = Collections.unmodifiableSet(rootChoiceNumbers);
        this.parentChoiceNumberByChoiceNumber = Collections.unmodifiableMap(parentChoiceNumberByChoiceNumber);
        this.references = Collections.unmodifiableList(references);
        this.legEventRampId = legEventRampId;
        this.totalChoices = totalChoices;
    }

    Map<Integer, Choice> choicesByNumber() {
        return choicesByNumber;
    }

    Set<Integer> rootChoiceNumbers() {
        return rootChoiceNumbers;
    }

    Map<Integer, Integer> parentChoiceNumberByChoiceNumber() {
        return parentChoiceNumberByChoiceNumber;
    }

    List<ChoiceReference> references() {
        return references;
    }

    String legEventRampId() {
        return legEventRampId;
    }

    int totalChoices() {
        return totalChoices;
    }
}
