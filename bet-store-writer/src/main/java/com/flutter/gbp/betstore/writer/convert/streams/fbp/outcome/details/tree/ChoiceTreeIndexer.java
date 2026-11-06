package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import com.flutter.obp.expression.proto.OutcomeExpressionOuterClass;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure parse phase that builds an {@link IndexedChoices} snapshot from the converted choices for one leg, logging
 * indexing failures and returning empty so the caller can clear the leg.
 *
 * <p>A blank or null {@code legEventRampId} short-circuits to {@link Optional#empty()} so malformed legs without an
 * event RAMP id cannot produce partial expressions.
 */
public class ChoiceTreeIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChoiceTreeIndexer.class);

    /**
     * Indexes the converted choices for one leg.
     *
     * @param choices converted choices for one leg
     * @param legEventRampId owning leg event RAMP id; blank values cause {@link Optional#empty()} to be returned
     * @return an Optional containing the immutable indexed choices snapshot, or empty when indexing failed
     */
    public Optional<IndexedChoices> index(List<Choice> choices, String legEventRampId) {
        if (StringUtils.isBlank(legEventRampId)) {
            return Optional.empty();
        }

        Map<Integer, Choice> choicesByNumber = new LinkedHashMap<>();
        Set<Integer> rootChoiceNumbers = new LinkedHashSet<>();
        Map<Integer, Integer> parentChoiceNumberByChoiceNumber = new LinkedHashMap<>();
        List<ChoiceReference> references = new ArrayList<>();
        int totalChoices = choices.size();

        for (Choice choice : choices) {
            int choiceNumber = choice.getChoiceNumber();

            if (!choice.hasOutcomeExpression()) {
                LOGGER.warn("Invalid choice tree: missing outcome expression, eventRampId={}, choiceNumbers={}, totalChoices={}",
                        legEventRampId, List.of(choiceNumber), totalChoices);
                return Optional.empty();
            }

            if (choicesByNumber.put(choiceNumber, choice) != null) {
                LOGGER.warn("Invalid choice tree: duplicate choice numbers, eventRampId={}, choiceNumbers={}, totalChoices={}",
                        legEventRampId, List.of(choiceNumber), totalChoices);
                return Optional.empty();
            }

            if (!choice.hasParentChoiceNumber()) {
                rootChoiceNumbers.add(choiceNumber);
            } else {
                parentChoiceNumberByChoiceNumber.put(choiceNumber, choice.getParentChoiceNumber().getValue());
            }

            Optional<List<ChoiceReference>> choiceReferences = collectChoiceReferences(choice, legEventRampId, totalChoices);
            if (choiceReferences.isEmpty()) {
                return Optional.empty();
            }
            references.addAll(choiceReferences.get());
        }

        return Optional.of(new IndexedChoices(
                choicesByNumber,
                rootChoiceNumbers,
                parentChoiceNumberByChoiceNumber,
                references,
                legEventRampId,
                totalChoices));
    }

    private Optional<List<ChoiceReference>> collectChoiceReferences(Choice choice,
                                                                    String legEventRampId,
                                                                    int totalChoices) {
        List<ChoiceReference> collected = new ArrayList<>();
        for (OutcomeExpressionOuterClass.OutcomeToken token : choice.getOutcomeExpression().getTokensList()) {
            switch (token.getTokenCase()) {
                case REFERENCE:
                    Optional<Integer> ref = parseChoiceReference(token.getReference().getValue());
                    if (ref.isEmpty()) {
                        LOGGER.warn("Invalid choice tree: malformed choice reference, eventRampId={}, references={}, totalChoices={}",
                                legEventRampId,
                                List.of(formatChoiceReference(choice.getChoiceNumber(),
                                        token.getReference().getValue(), "not_an_integer")),
                                totalChoices);
                        return Optional.empty();
                    }
                    collected.add(new ChoiceReference(choice.getChoiceNumber(), ref.get()));
                    break;
                case OUTCOME_DEFINITION:
                    if (!validateOutcomeDefinitionEvent(token, legEventRampId, totalChoices)) {
                        return Optional.empty();
                    }
                    break;
                default:
                    break;
            }
        }
        return Optional.of(collected);
    }

    private boolean validateOutcomeDefinitionEvent(OutcomeExpressionOuterClass.OutcomeToken token,
                                                   String legEventRampId,
                                                   int totalChoices) {
        String rampEventId = token.getOutcomeDefinition().getRampEventId();
        if (StringUtils.isBlank(rampEventId)) {
            LOGGER.warn("Invalid choice tree: outcome definition has blank event id, eventRampId={}, totalChoices={}",
                    legEventRampId, totalChoices);
            return false;
        }
        if (!legEventRampId.equals(rampEventId)) {
            LOGGER.warn("Invalid choice tree: outcome definition event does not match leg event, "
                            + "eventRampId={}, actualRampEventId={}, totalChoices={}",
                    legEventRampId, rampEventId, totalChoices);
            return false;
        }
        return true;
    }

    private Optional<Integer> parseChoiceReference(String referenceValue) {
        try {
            return Optional.of(Integer.parseInt(referenceValue));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String formatChoiceReference(int choiceNumber, String referenceValue, String reason) {
        return String.format("choice=%d,reference=%s,reason=%s", choiceNumber, referenceValue, reason);
    }
}
