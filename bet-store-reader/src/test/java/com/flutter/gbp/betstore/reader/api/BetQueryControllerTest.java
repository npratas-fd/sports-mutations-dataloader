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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    void betIdsAboveMaxIs400() throws Exception {
        String ids = IntStream.rangeClosed(1, properties.maxBetIds() + 1).mapToObj(i -> "b" + i)
                .collect(Collectors.joining(","));

        mvc.perform(get("/v1/bets").param("betIds", ids)).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/accounts/7/bets").param("betIds", ids)).andExpect(status().isBadRequest());
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

    private static Bet bet(String receiptId) {
        return Bet.newBuilder().setBetReceiptId(receiptId).setCurrency("GBP").build();
    }
}
