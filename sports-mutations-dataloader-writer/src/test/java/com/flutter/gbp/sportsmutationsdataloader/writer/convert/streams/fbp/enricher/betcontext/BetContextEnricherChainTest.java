package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils.BetContextDecoder;
import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.fcq.bet.Bet;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BetContextEnricherChainTest {

    @Mock
    private BetContextDecoder betContextDecoder;

    @Mock
    private BetContextEnricher enricher;

    private BetContextEnricherChain victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
        victim = new BetContextEnricherChain(betContextDecoder, List.of(enricher));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void testEnrich() {

        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder().build();

        BetDomainEventOuterClass.BetDomainEvent source =
                BetDomainEventOuterClass.BetDomainEvent
                        .newBuilder()
                        .setBet(bet)
                        .build();

        BetContext betContext = mock(BetContext.class);

        when(betContextDecoder.extractBetContext(bet)).thenReturn(Optional.of(betContext));

        Bet.Builder target = Bet.newBuilder();

        victim.enrich(source, target);

        verify(enricher).enrich(bet, betContext, target);
    }

    @Test
    public void testEnrichWithEmptyDecodedContext() {

        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder().build();

        BetDomainEventOuterClass.BetDomainEvent source =
                BetDomainEventOuterClass.BetDomainEvent
                        .newBuilder()
                        .setBet(bet)
                        .build();

        when(betContextDecoder.extractBetContext(bet)).thenReturn(Optional.empty());

        Bet.Builder target = Bet.newBuilder();

        victim.enrich(source, target);

        verifyNoInteractions(enricher);
    }

    @DataProvider
    public Object[][] invalidArguments() {
        return new Object[][]{
                {null, Bet.newBuilder()},
                {BetDomainEventOuterClass.BetDomainEvent.newBuilder().build(), null},
        };
    }

    @Test(dataProvider = "invalidArguments")
    public void testEnrichWithInvalidArguments(BetDomainEventOuterClass.BetDomainEvent source,
                                               Bet.Builder target) {
        victim.enrich(source, target);
    }
}
