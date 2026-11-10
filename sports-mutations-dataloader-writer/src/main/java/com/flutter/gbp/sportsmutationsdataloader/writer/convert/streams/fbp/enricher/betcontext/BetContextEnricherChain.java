package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils.BetContextDecoder;
import com.flutter.gbp.fcq.bet.Bet;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass;

import java.util.List;

import static java.util.Optional.ofNullable;

public class BetContextEnricherChain {

    private final BetContextDecoder betContextDecoder;

    private final List<BetContextEnricher> enrichers;

    public BetContextEnricherChain(BetContextDecoder betContextDecoder, List<BetContextEnricher> enrichers) {
        this.betContextDecoder = betContextDecoder;
        this.enrichers = enrichers;
    }

    public void enrich(BetDomainEventOuterClass.BetDomainEvent source, Bet.Builder target) {
        ofNullable(source)
                .map(BetDomainEventOuterClass.BetDomainEvent::getBet)
                .flatMap(betContextDecoder::extractBetContext)
                .ifPresent(betContext ->
                        enrichers.forEach(enricher ->
                                enricher.enrich(source.getBet(), betContext, target)));
    }
}
