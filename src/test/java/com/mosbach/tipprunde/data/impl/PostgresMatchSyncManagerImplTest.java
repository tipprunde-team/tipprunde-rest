package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// Idempotente Übernahme aus der Queue mit gemockter Datenbank, läuft auch in der CI ohne aiven
class PostgresMatchSyncManagerImplTest {

    private static final Instant KICKOFF = Instant.parse("2026-10-23T18:30:00Z");

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement selectStmt = mock(PreparedStatement.class);
    private final PreparedStatement insertStmt = mock(PreparedStatement.class);
    private final PreparedStatement updateStmt = mock(PreparedStatement.class);
    private final ResultSet selectRs = mock(ResultSet.class);
    private final ResultSet insertRs = mock(ResultSet.class);
    private final PostgresMatchSyncManagerImpl manager = new PostgresMatchSyncManagerImpl(dataSource);

    PostgresMatchSyncManagerImplTest() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("FOR UPDATE")))).thenReturn(selectStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("INSERT INTO matches"))))
                .thenReturn(insertStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("UPDATE matches"))))
                .thenReturn(updateStmt);
        when(selectStmt.executeQuery()).thenReturn(selectRs);
        when(insertStmt.executeQuery()).thenReturn(insertRs);
    }

    private static MatchUpdate update(MatchStatus status, Integer home, Integer away) {
        return new MatchUpdate(565833, 7, KICKOFF, status, "VfB Stuttgart", "Stuttgart",
                "https://crests.football-data.org/10.png", "Borussia Mönchengladbach", "M'gladbach",
                "https://crests.football-data.org/18.png", home, away);
    }

    // Spiel 565833 liegt schon in der Tabelle, mit Status, Toren und Korrektur-Flag wie angegeben
    private void existingRow(MatchStatus status, Integer home, Integer away, boolean manuallyCorrected)
            throws SQLException {
        OffsetDateTime kickoff = OffsetDateTime.ofInstant(KICKOFF, ZoneOffset.UTC);
        when(selectRs.next()).thenReturn(true);
        when(selectRs.getLong("id")).thenReturn(11L);
        when(selectRs.getLong("external_id")).thenReturn(565833L);
        when(selectRs.getInt("matchday")).thenReturn(7);
        when(selectRs.getObject("kickoff_utc", OffsetDateTime.class)).thenReturn(kickoff);
        when(selectRs.getString("status")).thenReturn(status.name());
        when(selectRs.getString("home_team")).thenReturn("VfB Stuttgart");
        when(selectRs.getString("home_team_short")).thenReturn("Stuttgart");
        when(selectRs.getString("home_crest")).thenReturn("https://crests.football-data.org/10.png");
        when(selectRs.getString("away_team")).thenReturn("Borussia Mönchengladbach");
        when(selectRs.getString("away_team_short")).thenReturn("M'gladbach");
        when(selectRs.getString("away_crest")).thenReturn("https://crests.football-data.org/18.png");
        when(selectRs.getObject("home_goals", Integer.class)).thenReturn(home);
        when(selectRs.getObject("away_goals", Integer.class)).thenReturn(away);
        when(selectRs.getBoolean("manually_corrected")).thenReturn(manuallyCorrected);
        when(selectRs.getObject("updated_at", OffsetDateTime.class)).thenReturn(kickoff);
    }

    @Test
    void newMatchIsInsertedAndCommitted() throws SQLException {
        when(selectRs.next()).thenReturn(false);
        when(insertRs.next()).thenReturn(true);
        when(insertRs.getLong(1)).thenReturn(99L);

        UpsertResult result = manager.upsert(update(MatchStatus.UPCOMING, null, null));

        assertEquals(new UpsertResult(Outcome.INSERTED, 99L, false), result);
        verify(selectStmt).setLong(1, 565833L);
        verify(insertStmt).setLong(1, 565833L);
        verify(insertStmt).setString(4, "UPCOMING");
        verify(insertStmt).setNull(11, Types.INTEGER);
        verify(insertStmt).setNull(12, Types.INTEGER);
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
    }

    @Test
    void sameStateWritesNothing() throws SQLException {
        existingRow(MatchStatus.FINISHED, 2, 3, false);

        UpsertResult result = manager.upsert(update(MatchStatus.FINISHED, 2, 3));

        assertEquals(new UpsertResult(Outcome.UNCHANGED, 11L, false), result);
        verify(insertStmt, never()).executeQuery();
        verify(updateStmt, never()).executeUpdate();
        verify(connection).commit();
    }

    @Test
    void finishedResultUpdatesRowAndMarksResultChanged() throws SQLException {
        existingRow(MatchStatus.UPCOMING, null, null, false);

        UpsertResult result = manager.upsert(update(MatchStatus.FINISHED, 2, 3));

        assertEquals(Outcome.UPDATED, result.outcome());
        assertEquals(11L, result.matchId());
        assertTrue(result.resultChanged());
        // Reihenfolge im UPDATE: matchday bis away_goals, zuletzt external_id im WHERE
        verify(updateStmt).setString(3, "FINISHED");
        verify(updateStmt).setInt(10, 2);
        verify(updateStmt).setInt(11, 3);
        verify(updateStmt).setLong(12, 565833L);
        verify(updateStmt).executeUpdate();
        verify(connection).commit();
    }

    @Test
    void manuallyCorrectedMatchIsNeverOverwritten() throws SQLException {
        existingRow(MatchStatus.FINISHED, 1, 1, true);

        UpsertResult result = manager.upsert(update(MatchStatus.FINISHED, 2, 3));

        assertEquals(Outcome.MANUALLY_CORRECTED, result.outcome());
        assertFalse(result.resultChanged());
        verify(updateStmt, never()).executeUpdate();
        verify(insertStmt, never()).executeQuery();
    }

    @Test
    void databaseErrorRollsBack() throws SQLException {
        existingRow(MatchStatus.UPCOMING, null, null, false);
        when(updateStmt.executeUpdate()).thenThrow(new SQLException("connection lost"));

        assertThrows(IllegalStateException.class, () -> manager.upsert(update(MatchStatus.FINISHED, 2, 3)));

        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
