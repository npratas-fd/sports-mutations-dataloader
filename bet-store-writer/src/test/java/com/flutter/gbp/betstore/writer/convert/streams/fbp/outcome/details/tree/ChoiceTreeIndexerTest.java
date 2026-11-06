package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import com.flutter.obp.expression.proto.OutcomeExpressionOuterClass;
import com.google.protobuf.Int32Value;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ChoiceTreeIndexerTest {

    private static final String LEG_EVENT_RAMP_ID = "20.111";

    private final ChoiceTreeIndexer victim = new ChoiceTreeIndexer();

    @Test
    public void testIndexValidTreeReturnsSnapshot() {
        Choice rootChoice = buildChoice(1, null, List.of(referenceToken(2), constIntegerToken(10)));
        Choice childChoice = buildChoice(2, 1, List.of(constIntegerToken(20)));

        Optional<IndexedChoices> result = victim.index(List.of(rootChoice, childChoice), LEG_EVENT_RAMP_ID);

        assertTrue(result.isPresent());
        IndexedChoices indexed = result.get();
        assertEquals(indexed.totalChoices(), 2);
        assertEquals(indexed.legEventRampId(), LEG_EVENT_RAMP_ID);
        assertEquals(indexed.choicesByNumber().keySet(), Set.of(1, 2));
        assertEquals(indexed.rootChoiceNumbers(), Set.of(1));
        assertEquals(indexed.parentChoiceNumberByChoiceNumber(), Map.of(2, 1));
        assertEquals(indexed.references().size(), 1);
        assertEquals(indexed.references().get(0).referencingChoiceNumber(), 1);
        assertEquals(indexed.references().get(0).referencedChoiceNumber(), 2);
    }

    @Test
    public void testIndexMissingOutcomeExpressionReturnsEmpty() {
        Choice rootChoice = Choice.newBuilder().setChoiceNumber(1).build();

        assertTrue(victim.index(List.of(rootChoice), LEG_EVENT_RAMP_ID).isEmpty());
    }

    @Test
    public void testIndexDuplicateChoiceNumberReturnsEmpty() {
        Choice rootChoice = buildChoice(1, null, List.of(constIntegerToken(10)));
        Choice duplicateChoice = buildChoice(1, 1, List.of(constIntegerToken(20)));

        assertTrue(victim.index(List.of(rootChoice, duplicateChoice), LEG_EVENT_RAMP_ID).isEmpty());
    }

    @Test
    public void testIndexMalformedReferenceReturnsEmpty() {
        Choice rootChoice = buildChoice(1, null, List.of(referenceToken("X")));

        assertTrue(victim.index(List.of(rootChoice), LEG_EVENT_RAMP_ID).isEmpty());
    }

    @Test
    public void testIndexBlankOutcomeDefinitionEventReturnsEmpty() {
        Choice rootChoice = buildChoice(1, null, List.of(outcomeDefinitionToken("", "Points_Match_Home")));

        assertTrue(victim.index(List.of(rootChoice), LEG_EVENT_RAMP_ID).isEmpty());
    }

    @Test
    public void testIndexOutcomeDefinitionRampEventIdDifferentFromLegEventRampIdReturnsEmpty() {
        Choice rootChoice = buildChoice(1, null, List.of(outcomeDefinitionToken("20.999", "Points_Match_Home")));

        assertTrue(victim.index(List.of(rootChoice), LEG_EVENT_RAMP_ID).isEmpty());
    }

    @Test
    public void testIndexBlankLegEventRampIdReturnsEmpty() {
        Choice rootChoice = buildChoice(1, null, List.of(constIntegerToken(10)));

        assertTrue(victim.index(List.of(rootChoice), "").isEmpty());
    }

    @Test
    public void testIndexNullLegEventRampIdReturnsEmpty() {
        Choice rootChoice = buildChoice(1, null, List.of(constIntegerToken(10)));

        assertTrue(victim.index(List.of(rootChoice), null).isEmpty());
    }

    private static Choice buildChoice(int choiceNumber,
                                      Integer parentChoiceNumber,
                                      List<OutcomeExpressionOuterClass.OutcomeToken> tokens) {
        Choice.Builder choiceBuilder = Choice.newBuilder()
                .setChoiceNumber(choiceNumber)
                .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder().addAllTokens(tokens).build());
        if (parentChoiceNumber != null) {
            choiceBuilder.setParentChoiceNumber(Int32Value.of(parentChoiceNumber));
        }
        return choiceBuilder.build();
    }

    private static OutcomeExpressionOuterClass.OutcomeToken referenceToken(int reference) {
        return referenceToken(String.valueOf(reference));
    }

    private static OutcomeExpressionOuterClass.OutcomeToken referenceToken(String reference) {
        return OutcomeExpressionOuterClass.OutcomeToken.newBuilder()
                .setReference(OutcomeExpressionOuterClass.OutcomeToken.Reference.newBuilder().setValue(reference).build())
                .build();
    }

    private static OutcomeExpressionOuterClass.OutcomeToken constIntegerToken(long value) {
        return OutcomeExpressionOuterClass.OutcomeToken.newBuilder().setConstInteger(value).build();
    }

    private static OutcomeExpressionOuterClass.OutcomeToken outcomeDefinitionToken(String rampEventId, String tradingId) {
        return OutcomeExpressionOuterClass.OutcomeToken.newBuilder()
                .setOutcomeDefinition(OutcomeExpressionOuterClass.OutcomeDefinition.newBuilder()
                        .setRampEventId(rampEventId)
                        .setTradingId(tradingId)
                        .build())
                .build();
    }
}
