package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass;
import com.flutter.gbp.fcq.bet.outcome.details.EventHierarchyOuterClass;
import com.flutter.gbp.fcq.bet.outcome.details.OutcomeBasedDetailsOuterClass.OutcomeBasedDetails;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class OutcomeBasedDetailsConverterTest {

    public static final String SPORT_NAME = "SportName";
    private static final String LEG_EVENT_RAMP_ID = "20.111";

    @Mock
    private ChoiceConverter choiceConverter;

    @Mock
    private EventHierarchyConverter eventHierarchyConverter;

    @InjectMocks
    private OutcomeBasedDetailsConverterImpl victim;

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
    public void testConvertNullOutcomeBasedDetails() {
        assertNull(victim.convert(null, LEG_EVENT_RAMP_ID));
    }

    @Test
    public void testConvertOutcomeBasedDetailsWithEmptyChoicesReturnsEmptyConvertedChoiceList() {
        when(choiceConverter.convertChoices(List.of(), LEG_EVENT_RAMP_ID)).thenReturn(List.of());

        OutcomeBasedDetails result = victim.convert(
                OutcomeBasedDetailsOuterClass.OutcomeBasedDetails.newBuilder().build(), LEG_EVENT_RAMP_ID);

        assertEquals(result.getChoicesList(), List.of());
        verify(choiceConverter).convertChoices(List.of(), LEG_EVENT_RAMP_ID);
    }

    @Test(dataProvider = "outcomeBasedDetailsProvider")
    public void testConvertOutcomeBasedDetails(Map<OutcomeBasedDetailsOuterClass.Choice, ChoiceOuterClass.Choice> expectedChoicesByInputChoices,
                                               Map<OutcomeBasedDetailsOuterClass.EventHierarchy, EventHierarchyOuterClass.EventHierarchy> expectedEventsByInputEvents) {
        List<OutcomeBasedDetailsOuterClass.Choice> inputChoices = List.copyOf(expectedChoicesByInputChoices.keySet());
        List<ChoiceOuterClass.Choice> expectedChoices = List.copyOf(expectedChoicesByInputChoices.values());

        when(victim.choiceConverter.convertChoices(inputChoices, LEG_EVENT_RAMP_ID)).thenReturn(expectedChoices);
        expectedEventsByInputEvents.forEach((key, value) -> when(victim.eventHierarchyConverter.convert(key)).thenReturn(value));

        OutcomeBasedDetailsOuterClass.OutcomeBasedDetails inputOutcomeBasedDetails = OutcomeBasedDetailsOuterClass.OutcomeBasedDetails.newBuilder()
                .addAllChoices(inputChoices)
                .addAllEvents(expectedEventsByInputEvents.keySet())
                .build();

        OutcomeBasedDetails result = victim.convert(inputOutcomeBasedDetails, LEG_EVENT_RAMP_ID);

        assertEquals(result.getChoicesList(), expectedChoices);
        assertEquals(result.getEventsList(), expectedEventsByInputEvents.values());
    }

    @DataProvider
    public static Object[][] outcomeBasedDetailsProvider() {
        return new Object[][]{
                {getExpectedChoiceByInputChoiceMap(), getExpectedEventHierarchyByInputEventHierarchyMap()}
        };
    }

    private static Map<OutcomeBasedDetailsOuterClass.EventHierarchy, EventHierarchyOuterClass.EventHierarchy> getExpectedEventHierarchyByInputEventHierarchyMap() {
        return new HashMap<>(
                Map.of(OutcomeBasedDetailsOuterClass.EventHierarchy.newBuilder().build(),
                        EventHierarchyOuterClass.EventHierarchy.newBuilder().build(),
                        OutcomeBasedDetailsOuterClass.EventHierarchy.newBuilder()
                                .setSportInfo(PartOuterClass.SportInfo.newBuilder().setEntity(
                                                PartOuterClass.Entity.newBuilder().setName(SPORT_NAME).build())
                                        .build())
                                .build(),
                        EventHierarchyOuterClass.EventHierarchy.newBuilder()
                                .setSportName(SPORT_NAME).build())
        );
    }

    private static Map<OutcomeBasedDetailsOuterClass.Choice, ChoiceOuterClass.Choice> getExpectedChoiceByInputChoiceMap() {
        Map<OutcomeBasedDetailsOuterClass.Choice, ChoiceOuterClass.Choice> expectedChoiceByInputChoiceMap = new LinkedHashMap<>();
        expectedChoiceByInputChoiceMap.put(
                OutcomeBasedDetailsOuterClass.Choice.newBuilder().build(),
                ChoiceOuterClass.Choice.newBuilder().build());
        expectedChoiceByInputChoiceMap.put(
                OutcomeBasedDetailsOuterClass.Choice.newBuilder().setParentChoiceNumber(Int32Value.of(1)).build(),
                ChoiceOuterClass.Choice.newBuilder().setParentChoiceNumber(Int32Value.of(1)).build());

        return expectedChoiceByInputChoiceMap;
    }
}
