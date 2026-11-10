package com.flutter.gbp.sportsmutationsdataloader.writer.convert;

import static org.assertj.core.api.Assertions.assertThat;

import com.flutter.gbp.fcq.bet.Bet;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass;
import com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass.BetDomainEvent;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Smoke test of the converter wiring: every {@code @Resource} of the copied mappers resolves and the toggles come
 * from {@code sports-mutations-dataloader.writer.toggles.*}.
 */
class ConverterConfigurationTest {

    @Test
    void wiresConvertersAndToggles() {
        try (AnnotationConfigApplicationContext context = context(Map.of(
                "sports-mutations-dataloader.writer.toggles.round-start-price-half-up", "true",
                "sports-mutations-dataloader.writer.toggles.market-based-obp-bets", "false"))) {

            assertThat(context.getBean(ConverterConfiguration.ROUND_START_PRICE_HALF_UP_TOGGLE, BooleanSupplier.class)
                    .getAsBoolean()).isTrue();
            assertThat(context.getBean(ConverterConfiguration.MARKET_BASED_OBP_BETS_TOGGLE, BooleanSupplier.class)
                    .getAsBoolean()).isFalse();

            Bet fbpBet = context.getBean("fbpBetConverter",
                    com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.bet.BetConverter.class).convert(fbpEvent());
            assertThat(fbpBet.getBetReceiptId()).isEqualTo("O/123456789/0001");
            assertThat(fbpBet.getLegsCount()).isEqualTo(1);

            Bet abpBet = context.getBean("abpBetConverter",
                    com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.bet.BetConverter.class).convert(abpEvent());
            assertThat(abpBet.getBetReceiptId()).isEqualTo("R/123456789/0001");
        }
    }

    @Test
    void togglesDefaultToOff() {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            assertThat(context.getBean(ConverterConfiguration.ROUND_START_PRICE_HALF_UP_TOGGLE, BooleanSupplier.class)
                    .getAsBoolean()).isFalse();
            assertThat(context.getBean(ConverterConfiguration.MARKET_BASED_OBP_BETS_TOGGLE, BooleanSupplier.class)
                    .getAsBoolean()).isFalse();
        }
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(ConverterConfiguration.class);
        context.refresh();
        return context;
    }

    private static BetDomainEventOuterClass.BetDomainEvent fbpEvent() {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder().setName("Selection").putIds("RAMP", "1")))
                .build();
        return BetDomainEventOuterClass.BetDomainEvent.newBuilder()
                .setBet(BetOuterClass.Bet.newBuilder()
                        .setAccountId("12345")
                        .setBetId("O/123456789/0001")
                        .setBetReceiptId("O/123456789/0001")
                        .addLegs(LegOuterClass.Leg.newBuilder()
                                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)
                                .addPart(part)))
                .build();
    }

    private static BetDomainEvent abpEvent() {
        return BetDomainEvent.newBuilder()
                .setBet(com.ppb.platform.sb.retail.proto.bet.BetOuterClass.Bet.newBuilder()
                        .setAccountId("12345")
                        .setBetId("R/123456789/0001")
                        .setBetReceiptId("R/123456789/0001")
                        .setChannel("otc"))
                .build();
    }
}
