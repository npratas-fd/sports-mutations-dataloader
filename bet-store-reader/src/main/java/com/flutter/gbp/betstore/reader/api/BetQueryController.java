package com.flutter.gbp.betstore.reader.api;

import com.flutter.gbp.betstore.api.BetList;
import com.flutter.gbp.betstore.cassandra.BetReadRepository;
import com.flutter.gbp.betstore.reader.config.ReaderProperties;
import com.flutter.gbp.fcq.bet.Bet;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read API: open bets by account (optionally filtered by bet ids) or by bet ids alone. Bets that do not exist are
 * simply absent from the response; there is no 404.
 */
@RestController
@RequestMapping(path = "/v1", produces = {"application/x-protobuf", "application/json"})
public class BetQueryController {

    /** Distinct bet ids asked for with {@code betIds}, per request; tag {@code endpoint}: {@code account} or {@code bets}. */
    static final String REQUESTED_METRIC = "betstore.reader.bet.ids.requested";
    /** Bets returned per request; tag {@code endpoint}: {@code account} or {@code bets}. */
    static final String RETURNED_METRIC = "betstore.reader.bets.returned";

    private final BetReadRepository repository;
    private final int maxBetIds;
    private final MeterRegistry meterRegistry;

    public BetQueryController(BetReadRepository repository, ReaderProperties properties, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.maxBetIds = properties.maxBetIds();
        this.meterRegistry = meterRegistry;
    }

    /**
     * Bets of an account; a missing or blank {@code betIds} means no filter.
     */
    @GetMapping("/accounts/{accountId}/bets")
    public BetList accountBets(@PathVariable("accountId") long accountId,
                               @RequestParam(name = "betIds", required = false) String betIds) {
        Set<String> ids = BetIdsParser.parse(betIds);
        List<Bet> bets;
        if (ids.isEmpty()) {
            bets = repository.findByAccount(accountId);
        } else {
            summary(REQUESTED_METRIC, "account").record(ids.size());
            bets = repository.findByAccountAndBetIds(accountId, checkMax(ids));
        }
        summary(RETURNED_METRIC, "account").record(bets.size());
        return BetList.newBuilder().addAllBets(bets).build();
    }

    /**
     * Bets by id, from any account; at least one id is required.
     */
    @GetMapping("/bets")
    public BetList bets(@RequestParam(name = "betIds", required = false) String betIds) {
        Set<String> ids = BetIdsParser.parse(betIds);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "betIds must contain at least one bet id");
        }
        summary(REQUESTED_METRIC, "bets").record(ids.size());
        List<Bet> bets = repository.findByBetIds(checkMax(ids));
        summary(RETURNED_METRIC, "bets").record(bets.size());
        return BetList.newBuilder().addAllBets(bets).build();
    }

    private DistributionSummary summary(String name, String endpoint) {
        return DistributionSummary.builder(name).tag("endpoint", endpoint).register(meterRegistry);
    }

    private Set<String> checkMax(Set<String> ids) {
        if (ids.size() > maxBetIds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "betIds must contain at most " + maxBetIds + " distinct ids");
        }
        return ids;
    }
}
