package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details.tree;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Validates the structural integrity of converted FCQ choice trees.
 */
public class ChoiceTreeValidator {

    private final ChoiceTreeIndexer choiceTreeIndexer;
    private final ChoiceTreeRules choiceTreeRules;

    /**
     * Creates a validator using the supplied indexer for parsing converted choices and rules for validating the
     * resulting snapshot.
     *
     * @param choiceTreeIndexer indexer that parses converted choices into an {@link IndexedChoices} snapshot
     * @param choiceTreeRules rules that validate the indexed choices snapshot
     */
    public ChoiceTreeValidator(ChoiceTreeIndexer choiceTreeIndexer,
                               ChoiceTreeRules choiceTreeRules) {
        checkArgument(choiceTreeIndexer != null, "choiceTreeIndexer must not be null");
        checkArgument(choiceTreeRules != null, "choiceTreeRules must not be null");
        this.choiceTreeIndexer = choiceTreeIndexer;
        this.choiceTreeRules = choiceTreeRules;
    }

    /**
     * Validates the structural integrity of converted choices without condensing.
     *
     * @param choices converted choices for one leg
     * @param legEventRampId owning leg event RAMP id; blank values are invalid so malformed legs without an event RAMP id
     *                       cannot produce partial expressions
     * @return {@code true} when choices are structurally valid, otherwise {@code false}
     */
    public boolean isChoiceTreeValid(List<Choice> choices, String legEventRampId) {
        if (choices == null || choices.isEmpty() || StringUtils.isBlank(legEventRampId)) {
            return false;
        }

        if (choices.size() == 1) {
            return true;
        }

        List<Choice> rootChoices = getRootChoices(choices);

        if (rootChoices.size() == choices.size()) {
            return true;
        }

        Optional<IndexedChoices> indexedChoices = choiceTreeIndexer.index(choices, legEventRampId);
        if (indexedChoices.isEmpty() || !choiceTreeRules.validate(indexedChoices.get())) {
            return false;
        }

        return true;
    }

    private List<Choice> getRootChoices(List<Choice> choices) {
        return choices.stream()
                .filter(choice -> !choice.hasParentChoiceNumber())
                .collect(Collectors.toList());
    }
}
