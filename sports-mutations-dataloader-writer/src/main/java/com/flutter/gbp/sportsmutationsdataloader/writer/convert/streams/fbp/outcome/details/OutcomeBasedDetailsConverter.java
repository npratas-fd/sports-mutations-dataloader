package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details;

import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import com.flutter.gbp.fcq.bet.outcome.details.EventHierarchyOuterClass.EventHierarchy;
import com.flutter.gbp.fcq.bet.outcome.details.OutcomeBasedDetailsOuterClass.OutcomeBasedDetails;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Mapper
public abstract class OutcomeBasedDetailsConverter {

    @Resource(name = "fbpChoiceConverter")
    protected ChoiceConverter choiceConverter;

    @Resource(name = "fbpEventHierarchyConverter")
    protected EventHierarchyConverter eventHierarchyConverter;

    @Mapping(target = "eventsList", ignore = true)
    @Mapping(target = "eventsOrBuilderList", ignore = true)
    @Mapping(target = "choicesList", ignore = true)
    @Mapping(target = "choicesOrBuilderList", ignore = true)
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract OutcomeBasedDetails convert(OutcomeBasedDetailsOuterClass.OutcomeBasedDetails outcomeBasedDetails,
                                                @Context String legEventRampId);

    @AfterMapping
    protected void convertOutcomeBasedDetails(OutcomeBasedDetailsOuterClass.OutcomeBasedDetails source,
                                              @MappingTarget OutcomeBasedDetails.Builder target,
                                              @Context String legEventRampId) {
        if (source == null) {
            return;
        }

        target.addAllEvents(getEventHierarchies(source));
        target.addAllChoices(getChoices(source, legEventRampId));
    }

    /**
     * Delegates choice conversion with the owning leg event id so the choice converter can validate and condense
     * multi-choice trees with that context.
     */
    private List<Choice> getChoices(OutcomeBasedDetailsOuterClass.OutcomeBasedDetails source, String legEventRampId) {
        return choiceConverter.convertChoices(source.getChoicesList(), legEventRampId);
    }

    /**
     * Converts each source event hierarchy independently because event hierarchy entries do not participate in the
     * choice-tree validation or condensing flow.
     */
    private List<EventHierarchy> getEventHierarchies(OutcomeBasedDetailsOuterClass.OutcomeBasedDetails source) {
        return source.getEventsList().stream()
                .map(eventHierarchyConverter::convert)
                .collect(Collectors.toList());
    }
}
