package com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details;

import com.flutter.gbp.fcq.bet.outcome.details.EventHierarchyOuterClass.EventHierarchy;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Optional;

@Mapper
public abstract class EventHierarchyConverter {
    private static final String RAMP_IDENTIFIER = "RAMP";

    @Mapping(target = "sportName", source = "eventHierarchy.sportInfo.entity.name")
    @Mapping(target = "competitionName", source = "eventHierarchy.competitionInfo.entity.name")
    @Mapping(target = "eventRampId", source = "eventHierarchy.eventInfo.entity", qualifiedByName = "convertEventRampId",
            conditionExpression = "java(hasEventRampId(eventHierarchy))")
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract EventHierarchy convert(OutcomeBasedDetailsOuterClass.EventHierarchy eventHierarchy);

    @Named("convertEventRampId")
    protected String convertEventRampId(PartOuterClass.Entity entity) {
        return entity.getIdsMap().get(RAMP_IDENTIFIER);
    }

    protected boolean hasEventRampId(OutcomeBasedDetailsOuterClass.EventHierarchy eventHierarchy) {
        return Optional.of(eventHierarchy)
                .filter(OutcomeBasedDetailsOuterClass.EventHierarchy::hasEventInfo)
                .map(OutcomeBasedDetailsOuterClass.EventHierarchy::getEventInfo)
                .filter(PartOuterClass.EventInfo::hasEntity)
                .map(PartOuterClass.EventInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
                .filter(map -> map.get(RAMP_IDENTIFIER) != null)
                .isPresent();
    }
}