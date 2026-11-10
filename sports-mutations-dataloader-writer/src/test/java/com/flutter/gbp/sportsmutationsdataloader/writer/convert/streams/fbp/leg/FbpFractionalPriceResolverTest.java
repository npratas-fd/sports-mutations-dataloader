package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.leg;

import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class FbpFractionalPriceResolverTest {

    private static final String BET_ID = "O/123456789/0001";
    private static final FractionalPriceOuterClass.FractionalPrice LEG_PRICE = fractionalPrice(4, 5);
    private static final FractionalPriceOuterClass.FractionalPrice FIRST_PART_PRICE = fractionalPrice(11, 13);
    private static final FractionalPriceOuterClass.FractionalPrice SECOND_PART_PRICE = fractionalPrice(3, 4);

    @Test(dataProvider = "resolveForPartScenarios")
    public void testResolveForPart(LegOuterClass.Leg leg, PartOuterClass.Part part,
                                  FractionalPriceOuterClass.FractionalPrice expectedPrice) {
        assertEquals(FbpFractionalPriceResolver.resolveForPart(leg, part), expectedPrice);
    }

    @DataProvider
    private Object[][] resolveForPartScenarios() {
        return new Object[][]{
                // The leg's combined price is the source of truth and wins over the Part's own price
                {legWithPrice(partWithPrice(FIRST_PART_PRICE)), partWithPrice(FIRST_PART_PRICE), LEG_PRICE},
                // Until FBP publishes the combined price, the Part this leg is quoted from prices it
                {legWithoutPrice(partWithPrice(FIRST_PART_PRICE)), partWithPrice(FIRST_PART_PRICE), FIRST_PART_PRICE},
                {legWithoutPrice(partWithoutPrice()), partWithoutPrice(), null},
                // A market-based leg is expanded per Part, so the Part given is the one that prices the leg
                {legWithoutPrice(partWithPrice(FIRST_PART_PRICE), partWithPrice(SECOND_PART_PRICE)),
                        partWithPrice(SECOND_PART_PRICE), SECOND_PART_PRICE},
                {null, partWithPrice(FIRST_PART_PRICE), FIRST_PART_PRICE},
                {legWithPrice(partWithPrice(FIRST_PART_PRICE)), null, LEG_PRICE},
                {legWithoutPrice(partWithPrice(FIRST_PART_PRICE)), null, null},
                {null, null, null}
        };
    }

    @Test(dataProvider = "resolveForGroupScenarios")
    public void testResolveForGroup(LegOuterClass.Leg leg, FractionalPriceOuterClass.FractionalPrice expectedPrice) {
        assertEquals(FbpFractionalPriceResolver.resolveForGroup(leg, BET_ID), expectedPrice);
    }

    @DataProvider
    private Object[][] resolveForGroupScenarios() {
        return new Object[][]{
                // The combined leg price is the price of the whole multiple choice group
                {legWithPrice(partWithPrice(FIRST_PART_PRICE)), LEG_PRICE},
                {legWithPrice(partWithPrice(FIRST_PART_PRICE), partWithPrice(SECOND_PART_PRICE)), LEG_PRICE},
                {legWithPrice(), LEG_PRICE},
                // A single Part's price is the price of the whole group, so it can stand in for the leg price
                {legWithoutPrice(partWithPrice(FIRST_PART_PRICE)), FIRST_PART_PRICE},
                {legWithoutPrice(partWithoutPrice()), null},
                // No individual Part price represents the combined price of a group of more than one Part
                {legWithoutPrice(partWithPrice(FIRST_PART_PRICE), partWithPrice(SECOND_PART_PRICE)), null},
                {legWithoutPrice(partWithPrice(FIRST_PART_PRICE), partWithoutPrice()), null},
                {legWithoutPrice(), null},
                {null, null}
        };
    }

    private static FractionalPriceOuterClass.FractionalPrice fractionalPrice(int numerator, int denominator) {
        return FractionalPriceOuterClass.FractionalPrice.newBuilder()
                .setCurrent(FractionOuterClass.Fraction.newBuilder()
                        .setNumerator(numerator)
                        .setDenominator(denominator)
                        .build())
                .build();
    }

    private static PartOuterClass.Part partWithPrice(FractionalPriceOuterClass.FractionalPrice price) {
        return PartOuterClass.Part.newBuilder().setFractionalPrice(price).build();
    }

    private static PartOuterClass.Part partWithoutPrice() {
        return PartOuterClass.Part.getDefaultInstance();
    }

    private static LegOuterClass.Leg legWithPrice(PartOuterClass.Part... parts) {
        return legBuilder(parts).setFractionalPrice(LEG_PRICE).build();
    }

    private static LegOuterClass.Leg legWithoutPrice(PartOuterClass.Part... parts) {
        return legBuilder(parts).build();
    }

    private static LegOuterClass.Leg.Builder legBuilder(PartOuterClass.Part... parts) {
        LegOuterClass.Leg.Builder builder = LegOuterClass.Leg.newBuilder().setLegNumber(1);
        for (PartOuterClass.Part part : parts) {
            builder.addPart(part);
        }
        return builder;
    }
}
