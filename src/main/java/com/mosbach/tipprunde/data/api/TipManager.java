package com.mosbach.tipprunde.data.api;

import java.util.List;

import com.mosbach.tipprunde.model.tip.Tip;

// Access to tips; submitting tips follows with US-20
public interface TipManager {

    // Own tips of one user in one group for all matches of a matchday
    List<Tip> getOwnTipsForMatchday(long groupId, long userId, int matchday);
}
