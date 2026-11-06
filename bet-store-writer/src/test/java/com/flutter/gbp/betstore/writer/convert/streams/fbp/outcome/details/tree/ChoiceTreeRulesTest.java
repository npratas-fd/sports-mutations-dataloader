package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import com.flutter.obp.expression.proto.OutcomeExpressionOuterClass;
import com.google.protobuf.Int32Value;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ChoiceTreeRulesTest {

    private static final String LEG_EVENT_RAMP_ID = "20.111";

    private final ChoiceTreeRules victim = new ChoiceTreeRules();

    @Test
    public void testValidateValidTreeReturnsTrue() {
        Choice rootChoice = buildChoice(1, null);
        Choice childChoice = buildChoice(2, 1);
        IndexedChoices indexed = build(
                Map.of(1, rootChoice, 2, childChoice),
                Set.of(1),
                Map.of(2, 1),
                List.of(new ChoiceReference(1, 2)));

        assertTrue(victim.validate(indexed));
    }

    @Test
    public void testValidateMissingRootReturnsFalse() {
        Choice choiceA = buildChoice(1, 2);
        Choice choiceB = buildChoice(2, 1);
        IndexedChoices indexed = build(
                Map.of(1, choiceA, 2, choiceB),
                Set.of(),
                Map.of(1, 2, 2, 1),
                List.of());

        assertFalse(victim.validate(indexed));
    }

    @Test
    public void testValidateMissingParentReferenceReturnsFalse() {
        Choice rootChoice = buildChoice(1, null);
        Choice orphanChoice = buildChoice(2, 99);
        IndexedChoices indexed = build(
                Map.of(1, rootChoice, 2, orphanChoice),
                Set.of(1),
                Map.of(2, 99),
                List.of());

        assertFalse(victim.validate(indexed));
    }

    @Test
    public void testValidateMissingReferencedChoiceReturnsFalse() {
        Choice rootChoice = buildChoice(1, null);
        IndexedChoices indexed = build(
                Map.of(1, rootChoice),
                Set.of(1),
                Map.of(),
                List.of(new ChoiceReference(1, 99)));

        assertFalse(victim.validate(indexed));
    }

    @Test
    public void testValidateReferencedChoiceIsRootReturnsFalse() {
        Choice rootChoiceA = buildChoice(1, null);
        Choice rootChoiceB = buildChoice(2, null);
        IndexedChoices indexed = build(
                Map.of(1, rootChoiceA, 2, rootChoiceB),
                Set.of(1, 2),
                Map.of(),
                List.of(new ChoiceReference(1, 2)));

        assertFalse(victim.validate(indexed));
    }

    @Test
    public void testValidateReferencedChoiceParentMismatchReturnsFalse() {
        Choice rootChoice = buildChoice(1, null);
        Choice otherRoot = buildChoice(2, null);
        Choice childOfOtherRoot = buildChoice(3, 2);
        IndexedChoices indexed = build(
                Map.of(1, rootChoice, 2, otherRoot, 3, childOfOtherRoot),
                Set.of(1, 2),
                Map.of(3, 2),
                List.of(new ChoiceReference(1, 3)));

        assertFalse(victim.validate(indexed));
    }

    @Test
    public void testValidateMultiLevelTreeAllReachablePasses() {
        Choice rootChoice = buildChoice(1, null);
        Choice childChoice = buildChoice(2, 1);
        Choice grandchildChoice = buildChoice(3, 2);
        IndexedChoices indexed = build(
                Map.of(1, rootChoice, 2, childChoice, 3, grandchildChoice),
                Set.of(1),
                Map.of(2, 1, 3, 2),
                List.of(new ChoiceReference(1, 2), new ChoiceReference(2, 3)));

        assertTrue(victim.validate(indexed));
    }

    @Test
    public void testValidateOrphanChildChoiceReturnsTrue() {
        Choice rootChoice = buildChoice(1, null);
        Choice reachableChild = buildChoice(2, 1);
        Choice unreachableChild = buildChoice(3, 1);
        IndexedChoices indexed = build(
                Map.of(1, rootChoice, 2, reachableChild, 3, unreachableChild),
                Set.of(1),
                Map.of(2, 1, 3, 1),
                List.of(new ChoiceReference(1, 2)));

        assertTrue(victim.validate(indexed));
    }

    @Test
    public void testValidateMultipleRootsAllBranchesReachablePasses() {
        Choice rootA = buildChoice(1, null);
        Choice rootB = buildChoice(2, null);
        Choice childA = buildChoice(3, 1);
        Choice childB = buildChoice(4, 2);
        IndexedChoices indexed = build(
                Map.of(1, rootA, 2, rootB, 3, childA, 4, childB),
                Set.of(1, 2),
                Map.of(3, 1, 4, 2),
                List.of(new ChoiceReference(1, 3), new ChoiceReference(2, 4)));

        assertTrue(victim.validate(indexed));
    }

    @Test
    public void testValidateMultipleRootsWithUnreachableNonRootReturnsTrue() {
        Choice rootA = buildChoice(1, null);
        Choice rootB = buildChoice(2, null);
        Choice childA = buildChoice(3, 1);
        Choice unreachableChild = buildChoice(4, 2);
        IndexedChoices indexed = build(
                Map.of(1, rootA, 2, rootB, 3, childA, 4, unreachableChild),
                Set.of(1, 2),
                Map.of(3, 1, 4, 2),
                List.of(new ChoiceReference(1, 3)));

        assertTrue(victim.validate(indexed));
    }

    private static IndexedChoices build(Map<Integer, Choice> choicesByNumber,
                                        Set<Integer> rootChoiceNumbers,
                                        Map<Integer, Integer> parentChoiceNumberByChoiceNumber,
                                        List<ChoiceReference> references) {
        return new IndexedChoices(
                new LinkedHashMap<>(choicesByNumber),
                new LinkedHashSet<>(rootChoiceNumbers),
                new LinkedHashMap<>(parentChoiceNumberByChoiceNumber),
                new ArrayList<>(references),
                LEG_EVENT_RAMP_ID,
                choicesByNumber.size());
    }

    private static Choice buildChoice(int choiceNumber, Integer parentChoiceNumber) {
        Choice.Builder choiceBuilder = Choice.newBuilder()
                .setChoiceNumber(choiceNumber)
                .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder().build());
        if (parentChoiceNumber != null) {
            choiceBuilder.setParentChoiceNumber(Int32Value.of(parentChoiceNumber));
        }
        return choiceBuilder.build();
    }
}
