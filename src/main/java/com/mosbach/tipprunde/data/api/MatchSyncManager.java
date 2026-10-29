package com.mosbach.tipprunde.data.api;

import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;

// Schreibzugriff auf Spiele für den Abgleich über die Queue (TE-02, US-18)
public interface MatchSyncManager {

    UpsertResult upsert(MatchUpdate update);
}
