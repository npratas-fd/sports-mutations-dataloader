package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.FractionalOddsConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.OddsConverter;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegType;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.retail.proto.bet.EntityOuterClass;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.EachwayInfoOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class LegConverterTest {

    @Mock
    private LegQuotingDataConverter legQuotingDataConverter;
    @Mock
    private OddsConverter oddsConverter;
    @Mock
    private FractionalOddsConverter fractionalOddsConverter;
    @Mock
    private LegTypeConverter legTypeConverter;
    @Mock
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @InjectMocks
    private LegConverterImpl legConverter;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test(dataProvider = "testConvertDataProvider")
    public void testConvert(LegOuterClass.Leg leg, boolean expectedHasExchangeMarketId) {
        // Mock the behavior of dependent components
        when(legQuotingDataConverter.convert(leg, leg.getPart(0))).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);
        when(javaValueToProtoValueConverter.convertToTimestamp(1681551000L)).thenReturn(Timestamp.getDefaultInstance());
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_EVENT_DESCRIPTION")).thenReturn(StringValue.of("MOCK_EVENT_DESCRIPTION"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_MARKET_DESCRIPTION")).thenReturn(StringValue.of("MOCK_MARKET_DESCRIPTION"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_SELECTION_NAME")).thenReturn(StringValue.of("MOCK_SELECTION_NAME"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_SPORT_NAME")).thenReturn(StringValue.of("MOCK_SPORT_NAME"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_COMPETITION_NAME")).thenReturn(StringValue.of("MOCK_COMPETITION_NAME"));
        when(oddsConverter.convert(FractionOuterClass.Fraction.newBuilder()
                .setNumerator(11)
                .setDenominator(13)
                .build()))
                .thenReturn(Odds.getDefaultInstance());
        when(fractionalOddsConverter.convert(FractionOuterClass.Fraction.newBuilder()
                .setNumerator(4)
                .setDenominator(7)
                .build()))
                .thenReturn(FractionalOdds.getDefaultInstance());

        // Perform the conversion
        Leg result = legConverter.convert(leg.getPart(0), leg);

        // Assertions
        assertNotNull(result);
        assertEquals(result.getEventId(), Int64Value.of(12313));
        assertEquals(result.hasExchangeMarketId(), expectedHasExchangeMarketId);
        if (expectedHasExchangeMarketId) {
            assertEquals(result.getExchangeMarketId(), StringValue.of("1.213123"));
        } else {
            assertEquals(result.getExchangeMarketId(), StringValue.getDefaultInstance());
        }
        assertEquals(result.getEachwayPlaces(), 3);
        assertEquals(result.getEventDescription(), StringValue.of("MOCK_EVENT_DESCRIPTION"));
        assertEquals(result.getEventMarketDescription(), StringValue.of("MOCK_MARKET_DESCRIPTION"));
        assertEquals(result.getSelectionName(), StringValue.of("MOCK_SELECTION_NAME"));
        assertEquals(result.getHandicap(), DoubleValue.of(2d));
        assertEquals(result.getSportName(), StringValue.of("MOCK_SPORT_NAME"));
        assertEquals(result.getCompetitionName(), StringValue.of("MOCK_COMPETITION_NAME"));
        assertEquals(result.getLegQuotingData(), LegQuotingData.newBuilder().build());
        assertEquals(result.getStartingPrice(), Odds.getDefaultInstance());
        assertEquals(result.getFrontEndEachwayFactor(), FractionalOdds.getDefaultInstance());
        assertEquals(result.getStartTime(), Timestamp.getDefaultInstance());
    }

    @DataProvider
    public Object[][] testConvertDataProvider() {
        return new Object[][]{
                {getDefaultLeg(true), true},
                {getDefaultLeg(false), false}
        };
    }

    @Nonnull
    private LegOuterClass.Leg getDefaultLeg(boolean withExchangeMarketId) {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setEventInfo(PartOuterClass.EventInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .putIds("SPORTEX", "12313")
                                .setName("MOCK_EVENT_DESCRIPTION")
                                .build())
                        .setStartTime(1681551000L)
                        .build())
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .putIds(withExchangeMarketId ? "EXCHANGE" : "UNUSED", "1.213123")
                                .setName("MOCK_MARKET_DESCRIPTION")
                                .build())
                        .build())
                .setEachwayInfo(EachwayInfoOuterClass.EachwayInfo.newBuilder()
                        .setEachwayPlaces(3)
                        .setEachwayFactor(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(4)
                                .setDenominator(7)
                                .build())
                        .build())
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .setName("MOCK_SELECTION_NAME")
                                .build())
                        .build())
                .setHandicap(DoubleValue.of(2d))
                .setSportInfo(PartOuterClass.SportInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .setName("MOCK_SPORT_NAME")
                                .build())
                        .build())
                .setCompetitionInfo(PartOuterClass.CompetitionInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .setName("MOCK_COMPETITION_NAME")
                                .build())
                        .build())
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(11)
                                .setDenominator(13)
                                .build())
                        .build())
                .build();

        return LegOuterClass.Leg.newBuilder()
                .addPart(part)
                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)
                .build();
    }
}