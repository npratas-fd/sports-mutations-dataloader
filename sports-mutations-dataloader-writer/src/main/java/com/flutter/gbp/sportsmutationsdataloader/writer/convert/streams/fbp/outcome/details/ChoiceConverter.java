package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details.tree.ChoiceTreeValidator;
import com.flutter.gbp.fcq.bet.outcome.details.ChoiceResult;
import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass.Choice;
import com.flutter.gbp.fcq.bet.outcome.details.TemplateInfoOuterClass.TemplateInfo;
import com.google.common.collect.ImmutableMap;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass.ChoiceResultContext;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class ChoiceConverter {

    private static final Map<ChoiceResultContext.ChoiceResult, ChoiceResult> RESULT_MAPPINGS = getResultMappings();

    @Resource(name = "choiceTreeValidator")
    protected ChoiceTreeValidator choiceTreeValidator;

    @Mapping(target = "choiceNumber", source = "choice.choiceNumber")
    @Mapping(target = "choiceId", source = "choice.choiceId")
    @Mapping(target = "parentChoiceNumber", source = "choice.parentChoiceNumber")
    @Mapping(target = "outcomeExpression", source = "choice.outcomeExpression")
    @Mapping(target = "result", source = "choice.resultContext.resultStatus", qualifiedByName = "toChoiceResult",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "supplementaryExpressions.stakeBackExpression", source = "choice.supplementaryExpressions.stakeBack")
    @Mapping(target = "supplementaryExpressions.priceSgxExpression", source = "choice.supplementaryExpressions.priceSgx")
    @Mapping(target = "templateInfo", source = "choice", qualifiedByName = "convertTemplateInfo",
            conditionExpression = "java(choice.hasTemplateInfo())")
    @Mapping(target = "stakeBackFactor", source = "choice.priceVariationFactors.stakeBackFactor",
            conditionExpression = "java(choice.hasPriceVariationFactors() && choice.getPriceVariationFactors().hasStakeBackFactor())")
    @Mapping(target = "voidFactor", source = "choice.priceVariationFactors.voidFactor",
            conditionExpression = "java(choice.hasPriceVariationFactors() && choice.getPriceVariationFactors().hasVoidFactor())")
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    @Mapping(target = "supplementaryExpressions.stakeBack", ignore = true)
    @Mapping(target = "supplementaryExpressions.unknownFields", ignore = true)
    @Mapping(target = "supplementaryExpressions.allFields", ignore = true)
    public abstract Choice convert(OutcomeBasedDetailsOuterClass.Choice choice);

  @Named("convertTemplateInfo")
  protected TemplateInfo convertTemplateInfo(OutcomeBasedDetailsOuterClass.Choice choice) {
    return TemplateInfo.newBuilder()
            .setTemplateId(choice.getTemplateInfo().getTemplateId())
            .build();
  }

    @Named("toChoiceResult")
    protected ChoiceResult toChoiceResult(ChoiceResultContext.ChoiceResult resultStatus) {
        return RESULT_MAPPINGS.getOrDefault(resultStatus, ChoiceResult.UNKNOWN_CHOICE_RESULT);
    }

    private static Map<ChoiceResultContext.ChoiceResult, ChoiceResult> getResultMappings() {
        return new ImmutableMap.Builder<ChoiceResultContext.ChoiceResult, ChoiceResult>()
                .put(ChoiceResultContext.ChoiceResult.CHOICE_RESULT_WON, ChoiceResult.WON)
                .put(ChoiceResultContext.ChoiceResult.CHOICE_RESULT_LOST, ChoiceResult.LOST)
                .put(ChoiceResultContext.ChoiceResult.CHOICE_RESULT_VOIDED, ChoiceResult.VOIDED)
                .put(ChoiceResultContext.ChoiceResult.CHOICE_RESULT_NONE, ChoiceResult.NONE)
                .build();
    }

    /**
     * Converts FBP choices and validates the choice tree structure.
     *
     * <p>Returns the full validated choice list so that all per-choice data (including supplementary expressions
     * such as stakeBack) is preserved for CBD storage. Condensing is deferred to the proto-to-domain conversion
     * boundary at quote time.
     *
     * @param sourceChoices FBP choices from the leg outcome-based details
     * @param legEventRampId owning leg event RAMP id; must be non-blank — blank values cause an empty list to be returned
     *                       so malformed legs without an event RAMP id cannot produce partial expressions
     * @return converted choices for valid choice trees, or an empty list for invalid trees or a blank legEventRampId
     */
    public List<Choice> convertChoices(List<OutcomeBasedDetailsOuterClass.Choice> sourceChoices, String legEventRampId) {
        if (sourceChoices == null || sourceChoices.isEmpty() || StringUtils.isBlank(legEventRampId)) {
            return Collections.emptyList();
        }

        List<Choice> convertedChoices = sourceChoices.stream()
                .map(this::convert)
                .collect(Collectors.toList());

        return choiceTreeValidator.isChoiceTreeValid(convertedChoices, legEventRampId) ? convertedChoices : Collections.emptyList();
    }

}
