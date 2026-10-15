package com.mosbach.tipprunde.data.api;

import java.util.List;
import java.util.Optional;

import com.mosbach.tipprunde.model.match.Match;

// Zugriff auf Spiele, später gefüllt durch den Match-Service
public interface MatchManager {

    List<Match> getMatchesByMatchday(int matchday);

    Optional<Match> getMatchById(long id);

    int getCurrentMatchday();
}