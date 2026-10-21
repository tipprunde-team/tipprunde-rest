package com.mosbach.tipprunde.data.api;

import java.time.Instant;
import java.util.List;

import com.mosbach.tipprunde.model.tip.Tip;

// Access to tips of one user in one group
public interface TipManager {

    // Own tips of one user in one group for all matches of a matchday
    List<Tip> getOwnTipsForMatchday(long groupId, long userId, int matchday);

    // Creates the tip or replaces the existing one (one tip per group, user and match)
    Tip saveTip(long groupId, long userId, long matchId, int home, int away, Instant now);
}
