package com.mosbach.tipprunde.data.api;

import java.util.List;

import com.mosbach.tipprunde.model.ranking.RankingEntry;

// Rangliste einer Gruppe, die Mitgliedschaft prüft vorher GroupAccess
public interface RankingManager {

    // Alle Mitglieder mit Punktesumme (ohne Tipps 0) und Rang, bei matchday nur die Tipps dieses Spieltags
    List<RankingEntry> getRanking(long groupId, Integer matchday);
}
