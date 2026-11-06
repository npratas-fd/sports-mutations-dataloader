package com.flutter.gbp.betstore.reader.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.DriverTimeoutException;
import com.flutter.gbp.betstore.api.BetList;
import com.flutter.gbp.betstore.cassandra.BetReadRepository;
import com.flutter.gbp.betstore.reader.BetStoreReaderApplication;
import com.flutter.gbp.betstore.reader.config.ReaderProperties;
import com.flutter.gbp.fcq.bet.Bet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest
@EnableConfigurationProperties(ReaderProperties.class)
class BetQueryControllerTest {

    private static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReaderProperties properties;

    @MockitoBean
    private BetReadRepository repository;

    @Autowired
    private MeterRegistry meterRegistry;

    @TestConfiguration
    static class MetricsConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void accountWithoutBetIdsCallsFindByAccount() throws Exception {
        when(repository.findByAccount(7L)).thenReturn(List.of(bet("a"), bet("b")));

        MvcResult result = mvc.perform(get("/v1/accounts/7/bets"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(PROTOBUF))
                .andReturn();

        BetList body = BetList.parseFrom(result.getResponse().getContentAsByteArray());
        assertThat(body.getBetsList()).extracting(Bet::getBetReceiptId).containsExactly("a", "b");
    }

    @Test
    void accountWithBetIdsCallsFindByAccountAndBetIds() throws Exception {
        when(repository.findByAccountAndBetIds(7L, Set.of("a", "b"))).thenReturn(List.of(bet("a")));

        MvcResult result = mvc.perform(get("/v1/accounts/7/bets").param("betIds", "a, b,a"))
                .andExpect(status().isOk())
                .andReturn();

        BetList body = BetList.parseFrom(result.getResponse().getContentAsByteArray());
        assertThat(body.getBetsList()).extracting(Bet::getBetReceiptId).containsExactly("a");
        verify(repository, never()).findByAccount(any(Long.class));
    }

    @Test
    void accountWithBlankBetIdsBehavesAsNoFilter() throws Exception {
        when(repository.findByAccount(7L)).thenReturn(List.of(bet("a")));

        mvc.perform(get("/v1/accounts/7/bets").param("betIds", ""))
                .andExpect(status().isOk());

        verify(repository).findByAccount(7L);
    }

    @Test
    void nonNumericAccountIdIs400() throws Exception {
        mvc.perform(get("/v1/accounts/abc/bets"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void betIdsEndpointCallsFindByBetIds() throws Exception {
        when(repository.findByBetIds(new LinkedHashSet<>(List.of("x", "y")))).thenReturn(List.of(bet("x"), bet("y")));

        MvcResult result = mvc.perform(get("/v1/bets").param("betIds", "x,y"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(PROTOBUF))
                .andReturn();

        BetList body = BetList.parseFrom(result.getResponse().getContentAsByteArray());
        assertThat(body.getBetsList()).extracting(Bet::getBetReceiptId).containsExactly("x", "y");
    }

    @Test
    void betIdsEndpointWithoutIdsIs400() throws Exception {
        mvc.perform(get("/v1/bets")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/bets").param("betIds", " , ")).andExpect(status().isBadRequest());
    }

    @Test
    void betIdsEndpointAboveMaxBetIdsIs400() throws Exception {
        mvc.perform(get("/v1/bets").param("betIds", ids(properties.maxBetIds() + 1))).andExpect(status().isBadRequest());

        verify(repository, never()).findByBetIds(any());
    }

    @Test
    void betIdsEndpointAtMaxBetIdsIsAccepted() throws Exception {
        mvc.perform(get("/v1/bets").param("betIds", ids(properties.maxBetIds()))).andExpect(status().isOk());
    }

    /** FCQ accepts any number of ids for an account and caps the result (ORDER BY bet_id LIMIT 70), so must we. */
    @Test
    void accountEndpointAcceptsMoreThanMaxBetIdsAndLeavesTheCapToTheRepository() throws Exception {
        int count = properties.maxBetIds() + 1;
        when(repository.findByAccountAndBetIds(any(Long.class), any())).thenReturn(List.of(bet("b1")));

        mvc.perform(get("/v1/accounts/7/bets").param("betIds", ids(count))).andExpect(status().isOk());

        verify(repository).findByAccountAndBetIds(7L, IntStream.rangeClosed(1, count).mapToObj(i -> "b" + i)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    @Test
    void accountEndpointAtMaxAccountBetIdsIsAccepted() throws Exception {
        mvc.perform(get("/v1/accounts/7/bets").param("betIds", ids(properties.maxAccountBetIds())))
                .andExpect(status().isOk());
    }

    @Test
    void accountEndpointAboveMaxAccountBetIdsIs400() throws Exception {
        mvc.perform(get("/v1/accounts/7/bets").param("betIds", ids(properties.maxAccountBetIds() + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(repository, never()).findByAccountAndBetIds(any(Long.class), any());
    }

    @Test
    void defaultLimits() {
        assertThat(properties.betLimit()).isEqualTo(70);
        assertThat(properties.maxBetIds()).isEqualTo(70);
        assertThat(properties.maxAccountBetIds()).isEqualTo(1000);
    }

    private static String ids(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "b" + i).collect(Collectors.joining(","));
    }

    @Test
    void jsonWhenAcceptJson() throws Exception {
        when(repository.findByAccount(7L)).thenReturn(List.of(bet("a")));

        mvc.perform(get("/v1/accounts/7/bets").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bets[0].betReceiptId").value("a"))
                .andExpect(jsonPath("$.bets[0].currency").value("GBP"));
    }

    @Test
    void unknownBetsYieldEmptyList() throws Exception {
        when(repository.findByBetIds(Set.of("nope"))).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/v1/bets").param("betIds", "nope"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(BetList.parseFrom(result.getResponse().getContentAsByteArray()).getBetsCount()).isZero();
    }

    @Test
    void cassandraFailureIs503() throws Exception {
        when(repository.findByAccount(7L)).thenThrow(new DriverTimeoutException("timeout"));

        mvc.perform(get("/v1/accounts/7/bets"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void requestedAndReturnedBetsAreRecordedPerEndpoint() throws Exception {
        // The registry is shared by the tests of this context
        meterRegistry.clear();
        when(repository.findByAccount(7L)).thenReturn(List.of(bet("a"), bet("b"), bet("c")));
        when(repository.findByAccountAndBetIds(7L, Set.of("a", "z"))).thenReturn(List.of(bet("a")));
        when(repository.findByBetIds(Set.of("x", "y", "w"))).thenReturn(List.of(bet("x")));

        mvc.perform(get("/v1/accounts/7/bets"));
        mvc.perform(get("/v1/accounts/7/bets").param("betIds", "a,z"));
        mvc.perform(get("/v1/bets").param("betIds", "x,y,w"));

        // No filter: only the returned count is recorded
        assertThat(summary("betstore.reader.bet.ids.requested", "account").count()).isEqualTo(1);
        assertThat(summary("betstore.reader.bet.ids.requested", "account").totalAmount()).isEqualTo(2.0);
        assertThat(summary("betstore.reader.bets.returned", "account").count()).isEqualTo(2);
        assertThat(summary("betstore.reader.bets.returned", "account").totalAmount()).isEqualTo(4.0);
        assertThat(summary("betstore.reader.bet.ids.requested", "bets").totalAmount()).isEqualTo(3.0);
        assertThat(summary("betstore.reader.bets.returned", "bets").totalAmount()).isEqualTo(1.0);
    }

    private DistributionSummary summary(String name, String endpoint) {
        return meterRegistry.get(name).tag("endpoint", endpoint).summary();
    }

    private static Bet bet(String receiptId) {
        return Bet.newBuilder().setBetReceiptId(receiptId).setCurrency("GBP").build();
    }
}
