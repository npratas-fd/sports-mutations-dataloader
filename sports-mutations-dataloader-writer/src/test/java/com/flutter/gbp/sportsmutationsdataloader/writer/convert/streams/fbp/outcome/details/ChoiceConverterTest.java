package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details.tree.ChoiceTreeValidator;
import com.flutter.gbp.fcq.bet.outcome.details.ChoiceResult;
import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass;
import com.flutter.gbp.fcq.bet.outcome.details.TemplateInfoOuterClass;
import com.flutter.gbp.fcq.bet.outcome.expression.SupplementaryExpressionsOuterClass;
import com.flutter.obp.expression.proto.OutcomeExpressionOuterClass;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

public class ChoiceConverterTest {

    private static final Int32Value PARENT_CHOICE_NUMBER = Int32Value.of(1);
    private static final int CHOICE_NUMBER = 2;
    private static final String CHOICE_ID = "3";
    private static final String TEMPLATE_ID = "playerVsPlayer";

    @InjectMocks
    private ChoiceConverterImpl victim;

    @Mock
    private ChoiceTreeValidator choiceTreeValidator;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void testConvertNullChoice() {
        assertNull(victim.convert(null));
    }

    @Test(dataProvider = "choiceProvider")
    public void testConvertValidChoice(OutcomeBasedDetailsOuterClass.Choice choice, ChoiceOuterClass.Choice expectedChoice) {

        ChoiceOuterClass.Choice result = victim.convert(choice);

        assertEquals(result.getChoiceNumber(), expectedChoice.getChoiceNumber());
        assertEquals(result.getChoiceId(), expectedChoice.getChoiceId());
        assertEquals(result.getParentChoiceNumber(), expectedChoice.getParentChoiceNumber());
        assertEquals(result.getOutcomeExpression(), expectedChoice.getOutcomeExpression());
        assertEquals(result.getSupplementaryExpressions(), expectedChoice.getSupplementaryExpressions());
        assertEquals(result.hasTemplateInfo(), expectedChoice.hasTemplateInfo());
        assertEquals(result.getTemplateInfo(), expectedChoice.getTemplateInfo());
        assertEquals(result.getResult(), expectedChoice.getResult());
    }

    @Test(dataProvider = "choiceResultProvider")
    public void testConvertChoiceResult(OutcomeBasedDetailsOuterClass.Choice sourceChoice, ChoiceResult expectedResult) {
        assertEquals(victim.convert(sourceChoice).getResult(), expectedResult);
    }

    @Test(dataProvider = "factorProvider")
    public void testConvertFactors(Double stakeBackFactor, Double voidFactor) {
        OutcomeBasedDetailsOuterClass.PriceVariations.Builder factors = OutcomeBasedDetailsOuterClass.PriceVariations.newBuilder();
        if (stakeBackFactor != null) {
            factors.setStakeBackFactor(DoubleValue.of(stakeBackFactor));
        }
        if (voidFactor != null) {
            factors.setVoidFactor(DoubleValue.of(voidFactor));
        }
        OutcomeBasedDetailsOuterClass.Choice source = OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setPriceVariationFactors(factors)
                .build();

        ChoiceOuterClass.Choice result = victim.convert(source);
        assertEquals(result.hasStakeBackFactor(), stakeBackFactor != null);
        assertEquals(result.hasVoidFactor(), voidFactor != null);
        if (stakeBackFactor != null) {
            assertEquals(result.getStakeBackFactor().getValue(), stakeBackFactor.doubleValue());
        }
        if (voidFactor != null) {
            assertEquals(result.getVoidFactor().getValue(), voidFactor.doubleValue());
        }
    }

    @Test
    public void testConvertWithoutPriceVariationFactors() {
        ChoiceOuterClass.Choice result = victim.convert(OutcomeBasedDetailsOuterClass.Choice.newBuilder().build());
        assertFalse(result.hasStakeBackFactor());
        assertFalse(result.hasVoidFactor());
    }

    @DataProvider
    public static Object[][] factorProvider() {
        return new Object[][] {
                {null, null},
                {0.0, null},
                {null, 0.0},
                {0.25, 0.75},
                {0.0, 0.5},
                {0.5, 0.0}
        };
    }

    @Test(dataProvider = "nullOrEmptyChoicesProvider")
    public void testConvertChoicesNullOrEmptyChoicesReturnsEmptyList(List<OutcomeBasedDetailsOuterClass.Choice> choices) {
        List<ChoiceOuterClass.Choice> result = victim.convertChoices(choices, null);

        assertEquals(result, List.of());
        verifyNoInteractions(choiceTreeValidator);
    }

    @DataProvider
    public static Object[][] nullOrEmptyChoicesProvider() {
        return new Object[][]{
                {null},
                {List.of()}
        };
    }

    @Test(dataProvider = "blankLegEventRampIdProvider")
    public void testConvertChoicesBlankLegEventRampIdReturnsEmptyList(String legEventRampId) {
        OutcomeBasedDetailsOuterClass.Choice sourceChoice = OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(CHOICE_NUMBER)
                .build();

        List<ChoiceOuterClass.Choice> result = victim.convertChoices(List.of(sourceChoice), legEventRampId);

        assertEquals(result, List.of());
        verifyNoInteractions(choiceTreeValidator);
    }

    @Test
    public void testConvertChoicesSingleChoiceDelegatesToChoiceTreeCondensing() {
        OutcomeBasedDetailsOuterClass.Choice sourceChoice = OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(CHOICE_NUMBER)
                .build();
        ChoiceOuterClass.Choice convertedChoice = ChoiceOuterClass.Choice.newBuilder().setChoiceNumber(CHOICE_NUMBER).build();

        when(choiceTreeValidator.isChoiceTreeValid(eq(List.of(convertedChoice)), eq("20.111"))).thenReturn(true);

        List<ChoiceOuterClass.Choice> result = victim.convertChoices(List.of(sourceChoice), "20.111");

        assertEquals(result, List.of(convertedChoice));
        verify(choiceTreeValidator).isChoiceTreeValid(eq(List.of(convertedChoice)), eq("20.111"));
    }

    @Test
    public void testConvertChoicesSingleChoiceWithDifferentEventDelegatesToChoiceTreeCondensing() {
        OutcomeBasedDetailsOuterClass.Choice sourceChoice = OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(CHOICE_NUMBER)
                .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder()
                        .addTokens(OutcomeExpressionOuterClass.OutcomeToken.newBuilder()
                                .setOutcomeDefinition(OutcomeExpressionOuterClass.OutcomeDefinition.newBuilder()
                                        .setRampEventId("20.222")
                                        .setTradingId("Points_Match_Home")
                                        .build())
                                .build())
                        .build())
                .build();
        ChoiceOuterClass.Choice convertedChoice = victim.convert(sourceChoice);

        when(choiceTreeValidator.isChoiceTreeValid(eq(List.of(convertedChoice)), eq("20.111"))).thenReturn(true);

        List<ChoiceOuterClass.Choice> result = victim.convertChoices(List.of(sourceChoice), "20.111");

        assertEquals(result, List.of(convertedChoice));
        verify(choiceTreeValidator).isChoiceTreeValid(eq(List.of(convertedChoice)), eq("20.111"));
    }

    @Test
    public void testConvertChoicesMultipleChoicesDelegatesToChoiceTreeCondensing() {
        OutcomeBasedDetailsOuterClass.Choice firstSourceChoice = OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(1)
                .build();
        OutcomeBasedDetailsOuterClass.Choice secondSourceChoice = OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(2)
                .build();
        ChoiceOuterClass.Choice firstConvertedChoice = ChoiceOuterClass.Choice.newBuilder().setChoiceNumber(1).build();
        ChoiceOuterClass.Choice secondConvertedChoice = ChoiceOuterClass.Choice.newBuilder().setChoiceNumber(2).build();

        when(choiceTreeValidator.isChoiceTreeValid(eq(List.of(firstConvertedChoice, secondConvertedChoice)), eq("20.111")))
                .thenReturn(true);

        List<ChoiceOuterClass.Choice> result = victim.convertChoices(List.of(firstSourceChoice, secondSourceChoice), "20.111");

        assertEquals(result, List.of(firstConvertedChoice, secondConvertedChoice));
        verify(choiceTreeValidator).isChoiceTreeValid(eq(List.of(firstConvertedChoice, secondConvertedChoice)), eq("20.111"));
    }

    @Test
    public void testConvertChoicesConvertsEachResultBeforeTreeValidation() {
        OutcomeBasedDetailsOuterClass.Choice firstSourceChoice = getChoiceWithResult(1,
                OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult.CHOICE_RESULT_WON);
        OutcomeBasedDetailsOuterClass.Choice secondSourceChoice = getChoiceWithResult(2,
                OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult.CHOICE_RESULT_LOST);
        ChoiceOuterClass.Choice firstConvertedChoice = ChoiceOuterClass.Choice.newBuilder()
                .setChoiceNumber(1)
                .setResult(ChoiceResult.WON)
                .build();
        ChoiceOuterClass.Choice secondConvertedChoice = ChoiceOuterClass.Choice.newBuilder()
                .setChoiceNumber(2)
                .setResult(ChoiceResult.LOST)
                .build();

        when(choiceTreeValidator.isChoiceTreeValid(eq(List.of(firstConvertedChoice, secondConvertedChoice)), eq("20.111")))
                .thenReturn(true);

        List<ChoiceOuterClass.Choice> result = victim.convertChoices(
                List.of(firstSourceChoice, secondSourceChoice), "20.111");

        assertEquals(result, List.of(firstConvertedChoice, secondConvertedChoice));
        verify(choiceTreeValidator).isChoiceTreeValid(eq(List.of(firstConvertedChoice, secondConvertedChoice)), eq("20.111"));
    }

    @Test
    public void testConvertChoicesInvalidChoiceTreeReturnsEmptyList() {
        OutcomeBasedDetailsOuterClass.Choice sourceChoice = OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(CHOICE_NUMBER)
                .build();
        ChoiceOuterClass.Choice convertedChoice = ChoiceOuterClass.Choice.newBuilder().setChoiceNumber(CHOICE_NUMBER).build();

        when(choiceTreeValidator.isChoiceTreeValid(eq(List.of(convertedChoice)), eq("20.111"))).thenReturn(false);

        List<ChoiceOuterClass.Choice> result = victim.convertChoices(List.of(sourceChoice), "20.111");

        assertEquals(result, List.of());
        verify(choiceTreeValidator).isChoiceTreeValid(eq(List.of(convertedChoice)), eq("20.111"));
    }

    @DataProvider
    public static Object[][] choiceProvider() {
        return new Object[][]{
                {OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                        .setChoiceNumber(CHOICE_NUMBER)
                        .setChoiceId(CHOICE_ID)
                        .setParentChoiceNumber(PARENT_CHOICE_NUMBER)
                        .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder().build())
                        .build(),
                        ChoiceOuterClass.Choice.newBuilder()
                                .setChoiceNumber(CHOICE_NUMBER)
                                .setChoiceId(CHOICE_ID)
                                .setParentChoiceNumber(PARENT_CHOICE_NUMBER)
                                .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder().build()).build()
                },
                {OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                        .setChoiceNumber(CHOICE_NUMBER)
                        .setChoiceId(CHOICE_ID)
                        .setParentChoiceNumber(PARENT_CHOICE_NUMBER)
                        .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder()
                                .addTokens(OutcomeExpressionOuterClass.OutcomeToken.newBuilder()
                                        .setConstInteger(1)
                                        .build()))
                        .setSupplementaryExpressions(OutcomeBasedDetailsOuterClass.SupplementaryExpressions.newBuilder()
                                .setStakeBack(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder()
                                        .addTokens(OutcomeExpressionOuterClass.OutcomeToken.newBuilder()
                                                .setConstInteger(2)
                                                .build())
                                        .build()
                                )
                                .build())
                        .build(),
                        ChoiceOuterClass.Choice.newBuilder()
                                .setChoiceNumber(CHOICE_NUMBER)
                                .setChoiceId(CHOICE_ID)
                                .setParentChoiceNumber(PARENT_CHOICE_NUMBER)
                                .setOutcomeExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder()
                                        .addTokens(OutcomeExpressionOuterClass.OutcomeToken.newBuilder().setConstInteger(1).build())
                                        .build())
                                 .setSupplementaryExpressions(SupplementaryExpressionsOuterClass.SupplementaryExpressions.newBuilder()
                                         .setStakeBackExpression(OutcomeExpressionOuterClass.OutcomeExpression.newBuilder()
                                                 .addTokens(OutcomeExpressionOuterClass.OutcomeToken.newBuilder().setConstInteger(2).build())
                                                 .build()).build())
                                 .build()
                 },
                {OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                        .setChoiceNumber(CHOICE_NUMBER)
                        .setSupplementaryExpressions(OutcomeBasedDetailsOuterClass.SupplementaryExpressions.newBuilder()
                                .setPriceSgx(constIntegerExpression(3)))
                        .build(),
                        ChoiceOuterClass.Choice.newBuilder()
                                .setChoiceNumber(CHOICE_NUMBER)
                                .setSupplementaryExpressions(SupplementaryExpressionsOuterClass.SupplementaryExpressions.newBuilder()
                                        .setPriceSgxExpression(constIntegerExpression(3)))
                                .build()
                },
                {OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                        .setChoiceNumber(CHOICE_NUMBER)
                        .setSupplementaryExpressions(OutcomeBasedDetailsOuterClass.SupplementaryExpressions.newBuilder()
                                .setStakeBack(constIntegerExpression(2))
                                .setPriceSgx(constIntegerExpression(3)))
                        .build(),
                        ChoiceOuterClass.Choice.newBuilder()
                                .setChoiceNumber(CHOICE_NUMBER)
                                .setSupplementaryExpressions(SupplementaryExpressionsOuterClass.SupplementaryExpressions.newBuilder()
                                        .setStakeBackExpression(constIntegerExpression(2))
                                        .setPriceSgxExpression(constIntegerExpression(3)))
                                .build()
                },
                {OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                        .setTemplateInfo(OutcomeBasedDetailsOuterClass.TemplateInfo.newBuilder()
                                .setTemplateId(TEMPLATE_ID)
                                .setTemplateVersion(2)
                                .setParameters("{\"selectionId\":\"123\"}"))
                        .build(),
                        ChoiceOuterClass.Choice.newBuilder()
                                .setTemplateInfo(TemplateInfoOuterClass.TemplateInfo.newBuilder()
                                        .setTemplateId(TEMPLATE_ID))
                                .build()
                },
                {OutcomeBasedDetailsOuterClass.Choice.newBuilder().build(), ChoiceOuterClass.Choice.newBuilder().build()}
        };
    }

    @DataProvider
    public static Object[][] choiceResultProvider() {
        return new Object[][]{
                {getChoiceWithResult(CHOICE_NUMBER,
                        OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult.CHOICE_RESULT_NONE), ChoiceResult.NONE},
                {getChoiceWithResult(CHOICE_NUMBER,
                        OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult.CHOICE_RESULT_WON), ChoiceResult.WON},
                {getChoiceWithResult(CHOICE_NUMBER,
                        OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult.CHOICE_RESULT_LOST), ChoiceResult.LOST},
                {getChoiceWithResult(CHOICE_NUMBER,
                        OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult.CHOICE_RESULT_VOIDED), ChoiceResult.VOIDED},
                {getChoiceWithResult(CHOICE_NUMBER,
                        OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult.CHOICE_RESULT_UNSPECIFIED), ChoiceResult.UNKNOWN_CHOICE_RESULT},
                {OutcomeBasedDetailsOuterClass.Choice.newBuilder().setChoiceNumber(CHOICE_NUMBER).build(), ChoiceResult.UNKNOWN_CHOICE_RESULT},
                {getChoiceWithUnknownProtoValueResult(CHOICE_NUMBER), ChoiceResult.UNKNOWN_CHOICE_RESULT}
        };
    }

    private static OutcomeExpressionOuterClass.OutcomeExpression constIntegerExpression(int value) {
        return OutcomeExpressionOuterClass.OutcomeExpression.newBuilder()
                .addTokens(OutcomeExpressionOuterClass.OutcomeToken.newBuilder().setConstInteger(value))
                .build();
    }

    private static OutcomeBasedDetailsOuterClass.Choice getChoiceWithResult(
            int choiceNumber,
            OutcomeBasedDetailsOuterClass.ChoiceResultContext.ChoiceResult result) {
        return OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(choiceNumber)
                .setResultContext(OutcomeBasedDetailsOuterClass.ChoiceResultContext.newBuilder()
                        .setResultStatus(result)
                        .build())
                .build();
    }

    private static OutcomeBasedDetailsOuterClass.Choice getChoiceWithUnknownProtoValueResult(int choiceNumber) {
        return OutcomeBasedDetailsOuterClass.Choice.newBuilder()
                .setChoiceNumber(choiceNumber)
                .setResultContext(OutcomeBasedDetailsOuterClass.ChoiceResultContext.newBuilder()
                        .setResultStatusValue(999)
                        .build())
                .build();
    }

    @DataProvider
    public static Object[][] blankLegEventRampIdProvider() {
        return new Object[][]{
                {"  "},
                {null}
        };
    }
}
