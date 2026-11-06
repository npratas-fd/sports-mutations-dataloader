package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure validate phase over an {@link IndexedChoices} snapshot. Each rule is a side-effect-free query that logs the
 * first failing rule and returns {@code false}.
 */
public class ChoiceTreeRules {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChoiceTreeRules.class);

    boolean validate(IndexedChoices indexed) {
        return requireRootExists(indexed)
                && requireParentReferencesExist(indexed)
                && requireExpressionReferencesValid(indexed);
    }

    private boolean requireRootExists(IndexedChoices indexed) {
        if (!indexed.rootChoiceNumbers().isEmpty()) {
            return true;
        }
        LOGGER.warn("Invalid choice tree: expected at least 1 root choice but found 0, "
                        + "eventRampId={}, rootChoiceNumbers={}, totalChoices={}",
                indexed.legEventRampId(),
                new ArrayList<>(indexed.rootChoiceNumbers()),
                indexed.totalChoices());
        return false;
    }

    private boolean requireParentReferencesExist(IndexedChoices indexed) {
        for (Map.Entry<Integer, Integer> entry : indexed.parentChoiceNumberByChoiceNumber().entrySet()) {
            if (!indexed.choicesByNumber().containsKey(entry.getValue())) {
                LOGGER.warn("Invalid choice tree: missing parent choice references, eventRampId={}, references={}, totalChoices={}",
                        indexed.legEventRampId(),
                        List.of(formatParentReference(entry.getKey(), entry.getValue())),
                        indexed.totalChoices());
                return false;
            }
        }
        return true;
    }

    private boolean requireExpressionReferencesValid(IndexedChoices indexed) {
        for (ChoiceReference reference : indexed.references()) {
            Choice referenced = indexed.choicesByNumber().get(reference.referencedChoiceNumber());
            if (referenced == null) {
                LOGGER.warn("Invalid choice tree: referenced choice not found, eventRampId={}, references={}, totalChoices={}",
                        indexed.legEventRampId(),
                        List.of(formatChoiceReference(reference.referencingChoiceNumber(),
                                reference.referencedChoiceNumber(), "missing_target")),
                        indexed.totalChoices());
                return false;
            }
            if (!referenced.hasParentChoiceNumber()) {
                LOGGER.warn("Invalid choice tree: referenced choice is a root choice, eventRampId={}, references={}, totalChoices={}",
                        indexed.legEventRampId(),
                        List.of(formatChoiceReference(reference.referencingChoiceNumber(),
                                reference.referencedChoiceNumber(), "referenced_choice_has_no_parent")),
                        indexed.totalChoices());
                return false;
            }
            if (referenced.getParentChoiceNumber().getValue() != reference.referencingChoiceNumber()) {
                LOGGER.warn("Invalid choice tree: referenced choice parent does not match referencing choice, "
                                + "eventRampId={}, references={}, totalChoices={}",
                        indexed.legEventRampId(),
                        List.of(formatChoiceReference(reference.referencingChoiceNumber(),
                                reference.referencedChoiceNumber(), "referenced_choice_parent_mismatch")),
                        indexed.totalChoices());
                return false;
            }
        }
        return true;
    }

    private static String formatParentReference(int choiceNumber, int parentChoiceNumber) {
        return String.format("choice=%d,parent=%d", choiceNumber, parentChoiceNumber);
    }

    private static String formatChoiceReference(int choiceNumber, int referencedChoiceNumber, String reason) {
        return String.format("choice=%d,reference=%d,reason=%s", choiceNumber, referencedChoiceNumber, reason);
    }
}
