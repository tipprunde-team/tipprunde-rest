package com.mosbach.tipprunde.data.api;

import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;

// KI-Log AH-15 "Übernimm Spiele aus der Queue so, dass ein doppeltes Event nichts doppelt schreibt."
// Schreibzugriff auf Spiele für den Abgleich über die Queue (TE-02, US-18)
public interface MatchSyncManager {

    UpsertResult upsert(MatchUpdate update);
}
