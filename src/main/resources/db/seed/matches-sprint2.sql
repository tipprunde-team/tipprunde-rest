-- Transitional seed until the match service exists (Sprint 3).
-- Source: football-data.org, Bundesliga matchday 6 (finished) and 7 (upcoming), fetched 2026-10-19.
-- Idempotent: upsert via external_id, safe to run any number of times.
-- Goals only for FINISHED matches (result after 90 minutes).
-- Rows with manually_corrected = true are never overwritten.
-- Run manually in pgAdmin (Query Tool) against aiven. Only Arlind runs this script.

BEGIN;

INSERT INTO matches (external_id, matchday, kickoff_utc, status,
                     home_team, home_team_short, home_crest,
                     away_team, away_team_short, away_crest,
                     home_goals, away_goals)
VALUES
    (565828, 6, TIMESTAMPTZ '2026-10-16T18:30:00Z', 'FINISHED',
     'Eintracht Frankfurt', 'Frankfurt', 'https://crests.football-data.org/19.png',
     '1. FC Köln', '1. FC Köln', 'https://crests.football-data.org/1.png', 1, 0),
    (565821, 6, TIMESTAMPTZ '2026-10-17T13:30:00Z', 'FINISHED',
     '1. FC Union Berlin', 'Union Berlin', 'https://crests.football-data.org/28.png',
     'Borussia Dortmund', 'Dortmund', 'https://crests.football-data.org/4.png', 0, 0),
    (565822, 6, TIMESTAMPTZ '2026-10-17T13:30:00Z', 'FINISHED',
     'Hamburger SV', 'HSV', 'https://crests.football-data.org/7.png',
     'VfB Stuttgart', 'Stuttgart', 'https://crests.football-data.org/10.png', 1, 0),
    (565823, 6, TIMESTAMPTZ '2026-10-17T13:30:00Z', 'FINISHED',
     'SV Werder Bremen', 'Bremen', 'https://crests.football-data.org/12.png',
     'SC Paderborn 07', 'SC Paderborn', 'https://crests.football-data.org/29.png', 2, 2),
    (565824, 6, TIMESTAMPTZ '2026-10-17T13:30:00Z', 'FINISHED',
     'SV 07 Elversberg', 'Elversberg', 'https://crests.football-data.org/719.png',
     'FC Augsburg', 'Augsburg', 'https://crests.football-data.org/16.png', 4, 3),
    (565829, 6, TIMESTAMPTZ '2026-10-17T13:30:00Z', 'FINISHED',
     'FC Schalke 04', 'Schalke', 'https://crests.football-data.org/6.png',
     '1. FSV Mainz 05', 'Mainz', 'https://crests.football-data.org/15.png', 0, 1),
    (565825, 6, TIMESTAMPTZ '2026-10-17T16:30:00Z', 'FINISHED',
     'FC Bayern München', 'Bayern', 'https://crests.football-data.org/5.png',
     'RB Leipzig', 'RB Leipzig', 'https://crests.football-data.org/721.png', 6, 1),
    (565827, 6, TIMESTAMPTZ '2026-10-18T13:30:00Z', 'FINISHED',
     'Bayer 04 Leverkusen', 'Leverkusen', 'https://crests.football-data.org/3.png',
     'SC Freiburg', 'Freiburg', 'https://crests.football-data.org/17.png', 2, 1),
    (565826, 6, TIMESTAMPTZ '2026-10-18T15:30:00Z', 'FINISHED',
     'Borussia Mönchengladbach', 'M''gladbach', 'https://crests.football-data.org/18.png',
     'TSG 1899 Hoffenheim', 'Hoffenheim', 'https://crests.football-data.org/2.png', 1, 2),
    (565833, 7, TIMESTAMPTZ '2026-10-23T18:30:00Z', 'UPCOMING',
     'VfB Stuttgart', 'Stuttgart', 'https://crests.football-data.org/10.png',
     'Borussia Mönchengladbach', 'M''gladbach', 'https://crests.football-data.org/18.png', NULL, NULL),
    (565830, 7, TIMESTAMPTZ '2026-10-24T13:30:00Z', 'UPCOMING',
     '1. FSV Mainz 05', 'Mainz', 'https://crests.football-data.org/15.png',
     'SV Werder Bremen', 'Bremen', 'https://crests.football-data.org/12.png', NULL, NULL),
    (565831, 7, TIMESTAMPTZ '2026-10-24T13:30:00Z', 'UPCOMING',
     '1. FC Köln', '1. FC Köln', 'https://crests.football-data.org/1.png',
     'FC Schalke 04', 'Schalke', 'https://crests.football-data.org/6.png', NULL, NULL),
    (565832, 7, TIMESTAMPTZ '2026-10-24T13:30:00Z', 'UPCOMING',
     'RB Leipzig', 'RB Leipzig', 'https://crests.football-data.org/721.png',
     'SV 07 Elversberg', 'Elversberg', 'https://crests.football-data.org/719.png', NULL, NULL),
    (565834, 7, TIMESTAMPTZ '2026-10-24T13:30:00Z', 'UPCOMING',
     'SC Paderborn 07', 'SC Paderborn', 'https://crests.football-data.org/29.png',
     'Hamburger SV', 'HSV', 'https://crests.football-data.org/7.png', NULL, NULL),
    (565838, 7, TIMESTAMPTZ '2026-10-24T13:30:00Z', 'UPCOMING',
     'FC Augsburg', 'Augsburg', 'https://crests.football-data.org/16.png',
     '1. FC Union Berlin', 'Union Berlin', 'https://crests.football-data.org/28.png', NULL, NULL),
    (565837, 7, TIMESTAMPTZ '2026-10-24T16:30:00Z', 'UPCOMING',
     'Borussia Dortmund', 'Dortmund', 'https://crests.football-data.org/4.png',
     'Eintracht Frankfurt', 'Frankfurt', 'https://crests.football-data.org/19.png', NULL, NULL),
    (565835, 7, TIMESTAMPTZ '2026-10-25T14:30:00Z', 'UPCOMING',
     'TSG 1899 Hoffenheim', 'Hoffenheim', 'https://crests.football-data.org/2.png',
     'Bayer 04 Leverkusen', 'Leverkusen', 'https://crests.football-data.org/3.png', NULL, NULL),
    (565836, 7, TIMESTAMPTZ '2026-10-25T16:30:00Z', 'UPCOMING',
     'SC Freiburg', 'Freiburg', 'https://crests.football-data.org/17.png',
     'FC Bayern München', 'Bayern', 'https://crests.football-data.org/5.png', NULL, NULL)
ON CONFLICT (external_id) DO UPDATE SET
    matchday        = EXCLUDED.matchday,
    kickoff_utc     = EXCLUDED.kickoff_utc,
    status          = EXCLUDED.status,
    home_team       = EXCLUDED.home_team,
    home_team_short = EXCLUDED.home_team_short,
    home_crest      = EXCLUDED.home_crest,
    away_team       = EXCLUDED.away_team,
    away_team_short = EXCLUDED.away_team_short,
    away_crest      = EXCLUDED.away_crest,
    home_goals      = EXCLUDED.home_goals,
    away_goals      = EXCLUDED.away_goals,
    updated_at      = now()
WHERE matches.manually_corrected = FALSE;

COMMIT;

-- Check: expected 9 matches per matchday, 9 with result on matchday 6
SELECT matchday, COUNT(*) AS matches, COUNT(home_goals) AS with_result
FROM matches
WHERE matchday IN (6, 7)
GROUP BY matchday
ORDER BY matchday;
