package com.flutter.gbp.betstore.writer.convert.streams.fbp;

import com.flutter.gbp.fcq.bet.risk.RiskContext;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.RiskContextOuterClass;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RiskContextConverterTest {

    private final RiskContextConverter converter = new RiskContextConverter();

    @Test
    public void shouldConvertRiskContextWithLiabilityGroup() {
       RiskContextOuterClass.RiskContext source = RiskContextOuterClass.RiskContext.newBuilder()
                .setCustomerRisk(RiskContextOuterClass.RiskContext.CustomerRisk.newBuilder()
                    .setLiabilityGroup(StringValue.of("Regular Customer"))
                    .build())
                .build();

        Optional<RiskContext> result = converter.convert(source);

        assertTrue(result.isPresent());
        assertTrue(result.get().hasCustomerRisk());
        assertTrue(result.get().getCustomerRisk().hasLiabilityGroup());
        assertEquals(result.get().getCustomerRisk().getLiabilityGroup().getValue(), "REGULAR CUSTOMER");
    }

    @Test
    public void shouldReturnEmptyWhenLiabilityGroupIsMissing() {
        RiskContextOuterClass.RiskContext source = RiskContextOuterClass.RiskContext.newBuilder()
            .setCustomerRisk(RiskContextOuterClass.RiskContext.CustomerRisk.newBuilder()
                .build())
            .build();

        Optional<RiskContext> result = converter.convert(source);

        assertFalse(result.isPresent());
    }

    @Test
    public void shouldReturnEmptyWhenCustomerRiskIsMissing() {
        RiskContextOuterClass.RiskContext source = RiskContextOuterClass.RiskContext.newBuilder().build();

        Optional<RiskContext> result = converter.convert(source);

        assertFalse(result.isPresent());
    }
}