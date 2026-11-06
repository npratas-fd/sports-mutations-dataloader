package com.flutter.gbp.betstore.writer.convert.streams.fbp;

import com.flutter.gbp.fcq.bet.risk.RiskContext;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.RiskContextOuterClass;

import javax.annotation.Nonnull;
import java.util.Optional;

public class RiskContextConverter {

    public Optional<RiskContext> convert(@Nonnull RiskContextOuterClass.RiskContext source) {
        if (!source.hasCustomerRisk()
            || !source.getCustomerRisk().hasLiabilityGroup()) {
            return Optional.empty();
        }

        StringValue liabilityGroup = source.getCustomerRisk().getLiabilityGroup();
        StringValue upperCaseLiabilityGroup = StringValue.of(liabilityGroup.getValue().toUpperCase());

        RiskContext riskContext = RiskContext.newBuilder()
            .setCustomerRisk(RiskContext.CustomerRisk.newBuilder()
                .setLiabilityGroup(upperCaseLiabilityGroup)
                .build())
            .build();

        return Optional.of(riskContext);
    }
}
