package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.bet;

import com.flutter.gbp.fcq.bet.BetStatus;
import com.ppb.platform.sb.retail.proto.bet.status.BetStatusOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class BetStatusConverterTest {

    private BetStatusConverter victim;

    @BeforeMethod
    public void setup() {
        victim = new BetStatusConverter();
    }

    @Test(dataProvider = "betStatusConversionDataProvider")
    public void shouldTestBetStatusConversion(BetStatusOuterClass.BetStatus source, BetStatus target) {
        assertEquals(victim.convert(source), target);
    }

    @DataProvider
    private Object[][] betStatusConversionDataProvider() {
        return new Object[][]{
                {null, BetStatus.UNKNOWN_BET_STATUS},
                {BetStatusOuterClass.BetStatus.UNKNOWN_BET_STATUS, BetStatus.UNKNOWN_BET_STATUS},
                {BetStatusOuterClass.BetStatus.UNRECOGNIZED, BetStatus.UNKNOWN_BET_STATUS},
                {BetStatusOuterClass.BetStatus.ACTIVE, BetStatus.ACTIVE},
                {BetStatusOuterClass.BetStatus.SCANNED, BetStatus.ACTIVE},
                {BetStatusOuterClass.BetStatus.CANCELLED, BetStatus.CANCELLED},
                {BetStatusOuterClass.BetStatus.CASHED_OUT, BetStatus.CASHED_OUT}
        };
    }
}