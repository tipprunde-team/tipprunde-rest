package com.mosbach.tipprunde.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.MatchManager;
import com.mosbach.tipprunde.data.api.TipManager;
import com.mosbach.tipprunde.domain.TipRules;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.tip.Tip;
import com.mosbach.tipprunde.model.tip.TipRequest;
import com.mosbach.tipprunde.model.tip.TipResponse;
import com.mosbach.tipprunde.security.CurrentUserId;
import com.mosbach.tipprunde.security.GroupAccess;
import com.mosbach.tipprunde.security.Membership;

// Submit or change a tip until kickoff (US-20, US-21, UC-01)
@RestController
@RequestMapping("/api/groups/{groupId}/matches/{matchId}/tip")
public class TipController {

    private static final Logger log = LoggerFactory.getLogger(TipController.class);

    private final GroupAccess groupAccess;
    private final MatchManager matchManager;
    private final TipManager tipManager;
    private final Clock clock;

    public TipController(GroupAccess groupAccess, MatchManager matchManager, TipManager tipManager, Clock clock) {
        this.groupAccess = groupAccess;
        this.matchManager = matchManager;
        this.tipManager = tipManager;
        this.clock = clock;
    }

    // Check order: token (interceptor) > membership > match exists > TipRules > upsert
    @PutMapping
    public TipResponse submitTip(@PathVariable("groupId") long groupId, @PathVariable("matchId") long matchId,
            @RequestBody TipRequest request, @CurrentUserId long userId) {
        log.info("Tip submitted: groupId={}, matchId={}, userId={}", groupId, matchId, userId);

        Membership membership = groupAccess.requireMember(groupId, userId);
        Match match = matchManager.getMatchById(matchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND",
                        "Dieses Spiel gibt es nicht."));

        // Server time decides the lock, never the client clock
        Instant now = Instant.now(clock);
        int home = TipRules.parseGoals(request.home());
        int away = TipRules.parseGoals(request.away());
        TipRules.check(match, membership, home, away, now);

        Tip tip = tipManager.saveTip(groupId, userId, matchId, home, away, now);
        log.info("Tip saved: groupId={}, matchId={}, userId={}", groupId, matchId, userId);
        return new TipResponse(tip.matchId(), tip.homeGoals(), tip.awayGoals(),
                tip.updatedAt().truncatedTo(ChronoUnit.MILLIS));
    }
}
