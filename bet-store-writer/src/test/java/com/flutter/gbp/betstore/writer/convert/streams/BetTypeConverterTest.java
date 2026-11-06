package com.flutter.gbp.betstore.writer.convert.streams;

import com.flutter.gbp.fcq.bet.BetType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class BetTypeConverterTest<T extends Enum<T>> {

    private BetTypeConverter<T> victim;

    @BeforeMethod
    public void setUp() {
        victim = new BetTypeConverter<>();
    }
    @Test(dataProvider = "betTypeConversionDataProvider")
    public void shouldTestLegStatusConversion(T source, BetType target) {
        assertEquals(victim.convert(source), target);
    }

    @DataProvider
    private Object[][] betTypeConversionDataProvider() {
        return new Object[][]{
                {null, BetType.UNKNOWN_BET_TYPE},
                //FBP
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.UNRECOGNIZED, BetType.UNKNOWN_BET_TYPE},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.SINGLE, BetType.SINGLE},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.DOUBLE, BetType.DOUBLE},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TREBLE, BetType.TREBLE},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.FOURFOLD, BetType.FOURFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.FIVEFOLD, BetType.FIVEFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.SIXFOLD, BetType.SIXFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.SEVENFOLD, BetType.SEVENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.EIGHTFOLD, BetType.EIGHTFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.NINEFOLD, BetType.NINEFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TENFOLD, BetType.TENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.ELEVENFOLD, BetType.ELEVENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TWELVEFOLD, BetType.TWELVEFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.THIRTEENFOLD, BetType.THIRTEENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.FOURTEENFOLD, BetType.FOURTEENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.FIFTEENFOLD, BetType.FIFTEENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.SIXTEENFOLD, BetType.SIXTEENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.SEVENTEENFOLD, BetType.SEVENTEENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.EIGHTEENFOLD, BetType.EIGHTEENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.NINETEENFOLD, BetType.NINETEENFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TWENTYFOLD, BetType.TWENTYFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TWENTYONEFOLD, BetType.TWENTYONEFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TWENTYTWOFOLD, BetType.TWENTYTWOFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TWENTYTHREEFOLD, BetType.TWENTYTHREEFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TWENTYFOURFOLD, BetType.TWENTYFOURFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TWENTYFIVEFOLD, BetType.TWENTYFIVEFOLD},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.TRIXIE, BetType.TRIXIE},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.PATENT, BetType.PATENT},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.YANKEE, BetType.YANKEE},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.CANADIAN, BetType.CANADIAN},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.HEINZ, BetType.HEINZ},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.SUPER_HEINZ, BetType.SUPER_HEINZ},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.GOLIATH, BetType.GOLIATH},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.LUCKY_15, BetType.LUCKY_15},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.LUCKY_31, BetType.LUCKY_31},
                {com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass.BetType.LUCKY_63, BetType.LUCKY_63},
                //ABP
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.UNKNOWN_BET_TYPE, BetType.UNKNOWN_BET_TYPE},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.UNRECOGNIZED, BetType.UNKNOWN_BET_TYPE},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.SINGLE, BetType.SINGLE},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.DOUBLE, BetType.DOUBLE},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TREBLE, BetType.TREBLE},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.FOURFOLD, BetType.FOURFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.FIVEFOLD, BetType.FIVEFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.SIXFOLD, BetType.SIXFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.SEVENFOLD, BetType.SEVENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.EIGHTFOLD, BetType.EIGHTFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.NINEFOLD, BetType.NINEFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TENFOLD, BetType.TENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.ELEVENFOLD, BetType.ELEVENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TWELVEFOLD, BetType.TWELVEFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.THIRTEENFOLD, BetType.THIRTEENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.FOURTEENFOLD, BetType.FOURTEENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.FIFTEENFOLD, BetType.FIFTEENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.SIXTEENFOLD, BetType.SIXTEENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.SEVENTEENFOLD, BetType.SEVENTEENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.EIGHTEENFOLD, BetType.EIGHTEENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.NINETEENFOLD, BetType.NINETEENFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TWENTYFOLD, BetType.TWENTYFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TWENTYONEFOLD, BetType.TWENTYONEFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TWENTYTWOFOLD, BetType.TWENTYTWOFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TWENTYTHREEFOLD, BetType.TWENTYTHREEFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TWENTYFOURFOLD, BetType.TWENTYFOURFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TWENTYFIVEFOLD, BetType.TWENTYFIVEFOLD},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.TRIXIE, BetType.TRIXIE},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.PATENT, BetType.PATENT},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.YANKEE, BetType.YANKEE},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.CANADIAN, BetType.CANADIAN},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.HEINZ, BetType.HEINZ},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.SUPER_HEINZ, BetType.SUPER_HEINZ},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.GOLIATH, BetType.GOLIATH},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.LUCKY_15, BetType.LUCKY_15},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.LUCKY_31, BetType.LUCKY_31},
                {com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass.BetType.LUCKY_63, BetType.LUCKY_63},
        };
    }

}