-- Datenbankschema v1 (PostgreSQL), idempotent, mehrfach ausfuehrbar
-- Keine Token-Tabelle, weil die Anmeldung ueber JWT zustandslos ist.
-- Ausfuehren manuell mit pgAdmin (siehe README), nicht automatisch beim Start.

CREATE TABLE IF NOT EXISTS users (
	id BIGSERIAL PRIMARY KEY,
	email VARCHAR(254) NOT NULL UNIQUE,
	username VARCHAR(30) NOT NULL UNIQUE,
	password_hash VARCHAR(100) NOT NULL,
	is_system_admin BOOLEAN NOT NULL DEFAULT FALSE,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS groups (
	id BIGSERIAL PRIMARY KEY,
	name VARCHAR(50) NOT NULL,
	invite_code VARCHAR(16) NOT NULL UNIQUE,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS group_members (
	group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
	user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
	joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	PRIMARY KEY (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS matches (
	id BIGSERIAL PRIMARY KEY,
	external_id BIGINT NOT NULL UNIQUE,
	matchday INT NOT NULL,
	kickoff_utc TIMESTAMPTZ NOT NULL,
	status VARCHAR(12) NOT NULL
		CHECK (status IN ('UPCOMING', 'LIVE', 'FINISHED', 'POSTPONED', 'CANCELLED')),
	home_team VARCHAR(100) NOT NULL,
	home_team_short VARCHAR(30),
	home_crest VARCHAR(255),
	away_team VARCHAR(100) NOT NULL,
	away_team_short VARCHAR(30),
	away_crest VARCHAR(255),
	home_goals INT,
	away_goals INT,
	manually_corrected BOOLEAN NOT NULL DEFAULT FALSE,
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_matches_matchday ON matches (matchday);

CREATE TABLE IF NOT EXISTS tips (
	id BIGSERIAL PRIMARY KEY,
	group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
	user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	match_id BIGINT NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
	home_goals INT NOT NULL CHECK (home_goals BETWEEN 0 AND 20),
	away_goals INT NOT NULL CHECK (away_goals BETWEEN 0 AND 20),
	points INT,
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	UNIQUE (group_id, user_id, match_id)
);
