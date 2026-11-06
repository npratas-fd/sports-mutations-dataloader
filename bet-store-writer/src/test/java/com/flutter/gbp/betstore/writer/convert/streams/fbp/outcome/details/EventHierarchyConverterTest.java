package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details;

import com.flutter.gbp.fcq.bet.outcome.details.EventHierarchyOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class EventHierarchyConverterTest {

    public static final String RAMP = "RAMP";
    public static final String RAMP_ID = "1";
    public static final String SPORT_NAME = "SportName";
    public static final String COMPETITION_NAME = "CompetitionName";
    private final EventHierarchyConverter victim = new EventHierarchyConverterImpl();

    @Test
    public void testConvertNullEventHierarchy() {
        assertNull(victim.convert(null));
    }

    @Test(dataProvider = "eventHierarchyProvider")
    public void testConvert(OutcomeBasedDetailsOuterClass.EventHierarchy eventHierarchy,
                            EventHierarchyOuterClass.EventHierarchy expectedEventHierarchy) {
        EventHierarchyOuterClass.EventHierarchy result = victim.convert(eventHierarchy);

        assertEquals(result, expectedEventHierarchy);
    }

    private static PartOuterClass.EventInfo getEventInfo(Map<String, String> ids) {
        return PartOuterClass.EventInfo.newBuilder()
                .setEntity(getEntity(ids)).build();
    }

    private static PartOuterClass.CompetitionInfo getCompetitionInfo(String name) {
        return PartOuterClass.CompetitionInfo.newBuilder()
                .setEntity(getEntity(name)).build();
    }

    private static PartOuterClass.SportInfo getSportInfo(String name) {
        return PartOuterClass.SportInfo.newBuilder()
                .setEntity(getEntity(name)).build();
    }

    private static PartOuterClass.Entity getEntity(String name) {
        PartOuterClass.Entity.Builder builder = PartOuterClass.Entity.newBuilder();

        return name != null ? builder.setName(name).build() : builder.build();
    }

    private static PartOuterClass.Entity getEntity(Map<String, String> ids) {
        PartOuterClass.Entity.Builder builder = PartOuterClass.Entity.newBuilder();

        return ids != null ? builder.putAllIds(ids).build() : builder.build();
    }

    @DataProvider
    public static Object[][] eventHierarchyProvider() {
        return new Object[][]{
                {OutcomeBasedDetailsOuterClass.EventHierarchy.newBuilder()
                        .setSportInfo(getSportInfo(SPORT_NAME))
                        .setCompetitionInfo(getCompetitionInfo(COMPETITION_NAME))
                        .setEventInfo(getEventInfo(new HashMap<>(Map.of(RAMP, RAMP_ID))))
                        .build(),
                        EventHierarchyOuterClass.EventHierarchy.newBuilder()
                                .setSportName(SPORT_NAME)
                                .setCompetitionName(COMPETITION_NAME)
                                .setEventRampId(RAMP_ID).build()},
                {OutcomeBasedDetailsOuterClass.EventHierarchy.newBuilder().build(),
                        EventHierarchyOuterClass.EventHierarchy.newBuilder().build()}
        };
    }

    @Test
    public void testConvertEventRampId() {
        assertEquals(victim.convertEventRampId(PartOuterClass.Entity.newBuilder()
                .putAllIds(new HashMap<>(Map.of(RAMP, RAMP_ID))).build()), RAMP_ID);
    }
}