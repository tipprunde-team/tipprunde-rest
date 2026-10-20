-- Local test group until the group endpoints (US-08, US-09) are merged
-- Needs test-user.sql (tester) and the seed matches-sprint2.sql
-- Idempotent: safe to run several times; prefix "TEST Arlind" as agreed for the shared database
INSERT INTO groups (name, invite_code)
VALUES ('TEST Arlind Gruppe', 'TESTARLIND01')
ON CONFLICT (invite_code) DO NOTHING;

INSERT INTO group_members (group_id, user_id, role)
SELECT g.id, u.id, 'ADMIN'
FROM groups g, users u
WHERE g.invite_code = 'TESTARLIND01' AND u.email = 'test@tippseite.de'
ON CONFLICT (group_id, user_id) DO NOTHING;

-- One tip on the first match of matchday 7, so myTip is visible (US-20 replaces this later)
INSERT INTO tips (group_id, user_id, match_id, home_goals, away_goals)
SELECT g.id, u.id, m.id, 2, 1
FROM groups g, users u, matches m
WHERE g.invite_code = 'TESTARLIND01' AND u.email = 'test@tippseite.de' AND m.external_id = 565833
ON CONFLICT (group_id, user_id, match_id) DO NOTHING;

-- Check: this groupId goes into docs/rest-client/matches.http
SELECT g.id AS group_id, g.name, COUNT(gm.user_id) AS members
FROM groups g LEFT JOIN group_members gm ON gm.group_id = g.id
WHERE g.invite_code = 'TESTARLIND01'
GROUP BY g.id, g.name;

-- Cleanup after the demo:
-- DELETE FROM groups WHERE invite_code = 'TESTARLIND01';
