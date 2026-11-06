package com.flutter.gbp.betstore.reader.api;

import com.flutter.gbp.betstore.api.BetList;
import com.flutter.gbp.betstore.cassandra.BetReadRepository;
import com.flutter.gbp.betstore.reader.config.ReaderProperties;
import com.flutter.gbp.fcq.bet.Bet;
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

    private final BetReadRepository repository;
    private final int maxBetIds;

    public BetQueryController(BetReadRepository repository, ReaderProperties properties) {
        this.repository = repository;
        this.maxBetIds = properties.maxBetIds();
    }

    /**
     * Bets of an account; a missing or blank {@code betIds} means no filter.
     */
    @GetMapping("/accounts/{accountId}/bets")
    public BetList accountBets(@PathVariable("accountId") long accountId,
                               @RequestParam(name = "betIds", required = false) String betIds) {
        Set<String> ids = BetIdsParser.parse(betIds);
        List<Bet> bets = ids.isEmpty()
                ? repository.findByAccount(accountId)
                : repository.findByAccountAndBetIds(accountId, checkMax(ids));
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
        return BetList.newBuilder().addAllBets(repository.findByBetIds(checkMax(ids))).build();
    }

    private Set<String> checkMax(Set<String> ids) {
        if (ids.size() > maxBetIds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "betIds must contain at most " + maxBetIds + " distinct ids");
        }
        return ids;
    }
}
