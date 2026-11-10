package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details.tree;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import com.flutter.gbp.fcq.bet.outcome.details.ChoiceResult;
import com.flutter.obp.expression.proto.OutcomeExpressionOuterClass;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class ChoiceTreeValidatorTest {

    private static final String LEG_EVENT_RAMP_ID = "20.111";

    @Spy
    private ChoiceTreeIndexer choiceTreeIndexer = new ChoiceTreeIndexer();

    @Spy
    private ChoiceTreeRules choiceTreeRules = new ChoiceTreeRules();

    @InjectMocks
    private ChoiceTreeValidator victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test(dataProvider = "constructorArgumentProvider")
    public void testConstructorNullArgumentThrowsException(ChoiceTreeIndexer indexer,
                                                           ChoiceTreeRules rules) {
        expectThrows(IllegalArgumentException.class, () -> new ChoiceTreeValidator(indexer, rules));
    }

    @Test
    public void testValidateNullChoicesReturnsFalse() {
        boolean result = victim.isChoiceTreeValid(null, LEG_EVENT_RAMP_ID);

        assertFalse(result);
    }

    @Test
    public void testValidateNullLegEventRampIdReturnsFalse() {
        Choice choice = buildChoice(1, null, List.of(constIntegerToken(1)));

        boolean result = victim.isChoiceTreeValid(List.of(choice), null);

        assertFalse(result);
        verifyNoInteractions(choiceTreeIndexer, choiceTreeRules);
    }

    @Test
    public void testValidateBlankLegEventRampIdReturnsFalse() {
        Choice choice = buildChoice(1, null, List.of(constIntegerToken(1)));

        boolean result = victim.isChoiceTreeValid(List.of(choice), "  ");

        assertFalse(result);
        verifyNoInteractions(choiceTreeIndexer, choiceTreeRules);
    }

    @Test
    public void testValidateEmptyChoicesReturnsFalse() {
        boolean result = victim.isChoiceTreeValid(List.of(), LEG_EVENT_RAMP_ID);

        assertFalse(result);
    }

    @Test
    public void testValidateSingleChoiceReturnsTrue() {
        Choice choice = buildChoice(1, null, List.of(constIntegerToken(1)));

        boolean result = victim.isChoiceTreeValid(List.of(choice), LEG_EVENT_RAMP_ID);

        assertTrue(result);
        verifyNoInteractions(choiceTreeIndexer, choiceTreeRules);
    }

    @Test
    public void testValidateFlatRootChoicesReturnsTrue() {
        Choice firstChoice = buildChoice(1, null, List.of(constIntegerToken(7)));
        Choice secondChoice = buildChoice(2, null, List.of(constIntegerToken(9)));
        List<Choice> choices = List.of(firstChoice, secondChoice);

        boolean result = victim.isChoiceTreeValid(choices, LEG_EVENT_RAMP_ID);

        assertTrue(result);
        verifyNoInteractions(choiceTreeIndexer, choiceTreeRules);
    }

    @Test
    public void testValidateValidParentChoiceTreeReturnsTrue() {
        Choice rootChoice = buildChoice(1, null, List.of(referenceToken(2), constIntegerToken(3)));
        Choice childChoice = buildChoice(2, 1, List.of(constIntegerToken(2)));
        List<Choice> choices = List.of(rootChoice, childChoice);

        boolean result = victim.isChoiceTreeValid(choices, LEG_EVENT_RAMP_ID);

        assertTrue(result);
    }

    @Test
    public void testValidateIgnoresChoiceResultsForValidTree() {
        Choice rootChoice = buildChoice(1, null, List.of(referenceToken(2), constIntegerToken(3)))
                .toBuilder()
                .setResult(ChoiceResult.WON)
                .build();
        Choice childChoice = buildChoice(2, 1, List.of(constIntegerToken(2)))
                .toBuilder()
                .setResult(ChoiceResult.LOST)
                .build();

        assertTrue(victim.isChoiceTreeValid(List.of(rootChoice, childChoice), LEG_EVENT_RAMP_ID));
    }

    @Test
    public void testValidateInvalidParentReferenceReturnsFalse() {
        Choice rootChoice = buildChoice(1, null, List.of(referenceToken(2), constIntegerToken(3)));
        Choice childChoice = buildChoice(2, 99, List.of(constIntegerToken(2)));
        List<Choice> choices = List.of(rootChoice, childChoice);

        boolean result = victim.isChoiceTreeValid(choices, LEG_EVENT_RAMP_ID);

        assertFalse(result);
    }

    @Test
    public void testValidateInvalidExpressionReferenceReturnsFalse() {
        Choice rootChoice = buildChoice(1, null, List.of(referenceToken("X"), constIntegerToken(1)));
        Choice childChoice = buildChoice(2, 1, List.of(constIntegerToken(2)));
        List<Choice> choices = List.of(rootChoice, childChoice);

        boolean result = victim.isChoiceTreeValid(choices, LEG_EVENT_RAMP_ID);

        assertFalse(result);
    }

    @Test
    public void testValidateRejectsTheSameInvalidStructureRegardlessOfChoiceResults() {
        Choice rootChoice = buildChoice(1, null, List.of(referenceToken(99), constIntegerToken(1)))
                .toBuilder()
                .setResult(ChoiceResult.WON)
                .build();
        Choice childChoice = buildChoice(2, 1, List.of(constIntegerToken(2)))
                .toBuilder()
                .setResult(ChoiceResult.VOIDED)
                .build();

        assertFalse(victim.isChoiceTreeValid(List.of(rootChoice, childChoice), LEG_EVENT_RAMP_ID));
    }

    @DataProvider
    public static Object[][] constructorArgumentProvider() {
        return new Object[][]{
                {null, new ChoiceTreeRules()},
                {new ChoiceTreeIndexer(), null}
        };
    }

    private static Choice buildChoice(int choiceNumber,
                                      Integer parentChoiceNumber,
                                      List<OutcomeExpressionOuterClass.OutcomeToken> tokens) {
        Choice.Builder choiceBuilder = Choice.newBuilder()
                .setChoiceNumber(choiceNumber)
                .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder().addAllTokens(tokens).build());

        if (parentChoiceNumber != null) {
            choiceBuilder.setParentChoiceNumber(com.google.protobuf.Int32Value.of(parentChoiceNumber));
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
}
