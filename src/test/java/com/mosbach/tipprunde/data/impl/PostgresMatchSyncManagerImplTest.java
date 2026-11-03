package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
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
import java.util.Arrays;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// KI-Log AH-15 "Übernimm Spiele aus der Queue so, dass ein doppeltes Event nichts doppelt schreibt."
// KI-Log AH-16 "Berechne die Punkte der Tipps, sobald ein Spiel als beendet gemeldet wird."
// Idempotente Übernahme aus der Queue und Punkte der Tipps mit gemockter Datenbank, läuft auch in der CI ohne aiven
class PostgresMatchSyncManagerImplTest {

    private static final Instant KICKOFF = Instant.parse("2026-10-23T18:30:00Z");

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement selectStmt = mock(PreparedStatement.class);
    private final PreparedStatement insertStmt = mock(PreparedStatement.class);
    private final PreparedStatement updateStmt = mock(PreparedStatement.class);
    private final PreparedStatement allTipsStmt = mock(PreparedStatement.class);
    private final PreparedStatement unscoredTipsStmt = mock(PreparedStatement.class);
    private final PreparedStatement tipPointsStmt = mock(PreparedStatement.class);
    private final PreparedStatement clearPointsStmt = mock(PreparedStatement.class);
    private final ResultSet selectRs = mock(ResultSet.class);
    private final ResultSet insertRs = mock(ResultSet.class);
    private final ResultSet tipsRs = mock(ResultSet.class);
    private final PostgresMatchSyncManagerImpl manager = new PostgresMatchSyncManagerImpl(dataSource);

    PostgresMatchSyncManagerImplTest() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("FROM matches")
                && sql.contains("FOR UPDATE")))).thenReturn(selectStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("INSERT INTO matches"))))
                .thenReturn(insertStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("UPDATE matches"))))
                .thenReturn(updateStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("FROM tips")
                && !sql.contains("points IS NULL")))).thenReturn(allTipsStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("FROM tips")
                && sql.contains("points IS NULL")))).thenReturn(unscoredTipsStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("UPDATE tips SET points = ?"))))
                .thenReturn(tipPointsStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("UPDATE tips SET points = NULL"))))
                .thenReturn(clearPointsStmt);
        when(selectStmt.executeQuery()).thenReturn(selectRs);
        when(insertStmt.executeQuery()).thenReturn(insertRs);
        when(allTipsStmt.executeQuery()).thenReturn(tipsRs);
        when(unscoredTipsStmt.executeQuery()).thenReturn(tipsRs);
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

    // Tipps zum Spiel: je Tipp Heimtore, Gasttore; noch ohne Punkte
    private void tips(int[]... goals) throws SQLException {
        Boolean[] more = new Boolean[goals.length];
        Long[] ids = new Long[goals.length];
        Integer[] home = new Integer[goals.length];
        Integer[] away = new Integer[goals.length];
        for (int i = 0; i < goals.length; i++) {
            more[i] = i < goals.length - 1;
            ids[i] = (long) (i + 1);
            home[i] = goals[i][0];
            away[i] = goals[i][1];
        }
        when(tipsRs.next()).thenReturn(true, more);
        when(tipsRs.getLong("id")).thenReturn(ids[0], Arrays.copyOfRange(ids, 1, ids.length));
        when(tipsRs.getInt("home_goals")).thenReturn(home[0], Arrays.copyOfRange(home, 1, home.length));
        when(tipsRs.getInt("away_goals")).thenReturn(away[0], Arrays.copyOfRange(away, 1, away.length));
    }

    @Test
    void newMatchIsInsertedAndCommitted() throws SQLException {
        when(selectRs.next()).thenReturn(false);
        when(insertRs.next()).thenReturn(true);
        when(insertRs.getLong(1)).thenReturn(99L);

        UpsertResult result = manager.upsert(update(MatchStatus.UPCOMING, null, null));

        assertEquals(new UpsertResult(Outcome.INSERTED, 99L, false, 0), result);
        verify(selectStmt).setLong(1, 565833L);
        verify(insertStmt).setLong(1, 565833L);
        verify(insertStmt).setString(4, "UPCOMING");
        verify(insertStmt).setNull(11, Types.INTEGER);
        verify(insertStmt).setNull(12, Types.INTEGER);
        verify(allTipsStmt, never()).executeQuery();
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
    }

    @Test
    void sameStateWritesNothingAndOnlyLooksForUnscoredTips() throws SQLException {
        existingRow(MatchStatus.FINISHED, 2, 3, false);

        UpsertResult result = manager.upsert(update(MatchStatus.FINISHED, 2, 3));

        assertEquals(new UpsertResult(Outcome.UNCHANGED, 11L, false, 0), result);
        verify(insertStmt, never()).executeQuery();
        verify(updateStmt, never()).executeUpdate();
        verify(unscoredTipsStmt).setLong(1, 11L);
        verify(allTipsStmt, never()).executeQuery();
        verify(tipPointsStmt, never()).executeBatch();
        verify(connection).commit();
    }

    @Test
    void finishedResultUpdatesRowAndScoresAllTipsInTheSameTransaction() throws SQLException {
        existingRow(MatchStatus.UPCOMING, null, null, false);
        // Beispiele aus US-24 bei Ergebnis 3:1
        tips(new int[] {3, 1}, new int[] {2, 0}, new int[] {4, 0}, new int[] {1, 1});

        UpsertResult result = manager.upsert(update(MatchStatus.FINISHED, 3, 1));

        assertEquals(new UpsertResult(Outcome.UPDATED, 11L, true, 4), result);
        // Reihenfolge im UPDATE: matchday bis away_goals, zuletzt external_id im WHERE
        verify(updateStmt).setString(3, "FINISHED");
        verify(updateStmt).setInt(10, 3);
        verify(updateStmt).setInt(11, 1);
        verify(updateStmt).setLong(12, 565833L);
        verify(allTipsStmt).setLong(1, 11L);
        InOrder order = inOrder(tipPointsStmt, connection);
        order.verify(tipPointsStmt).setInt(1, 4);
        order.verify(tipPointsStmt).setLong(2, 1L);
        order.verify(tipPointsStmt).setInt(1, 3);
        order.verify(tipPointsStmt).setLong(2, 2L);
        order.verify(tipPointsStmt).setInt(1, 2);
        order.verify(tipPointsStmt).setLong(2, 3L);
        order.verify(tipPointsStmt).setInt(1, 0);
        order.verify(tipPointsStmt).setLong(2, 4L);
        order.verify(tipPointsStmt).executeBatch();
        order.verify(connection).commit();
    }

    @Test
    void tipWithCorrectPointsIsNotWrittenAgain() throws SQLException {
        existingRow(MatchStatus.FINISHED, 2, 2, false);
        tips(new int[] {1, 1});
        when(tipsRs.getObject("points", Integer.class)).thenReturn(3);

        // Ergebnis von 2:2 auf 3:3 korrigiert: Tipp 1:1 hat weiter die richtige Tordifferenz
        UpsertResult result = manager.upsert(update(MatchStatus.FINISHED, 3, 3));

        assertEquals(0, result.tipsScored());
        verify(tipPointsStmt, never()).executeBatch();
    }

    @Test
    void cancelledMatchLosesItsPoints() throws SQLException {
        existingRow(MatchStatus.FINISHED, 2, 3, false);
        when(clearPointsStmt.executeUpdate()).thenReturn(5);

        UpsertResult result = manager.upsert(update(MatchStatus.CANCELLED, null, null));

        assertEquals(new UpsertResult(Outcome.UPDATED, 11L, false, 5), result);
        verify(clearPointsStmt).setLong(1, 11L);
        verify(allTipsStmt, never()).executeQuery();
    }

    @Test
    void manuallyCorrectedMatchIsNeverOverwrittenAndScoredWithTheStoredResult() throws SQLException {
        existingRow(MatchStatus.FINISHED, 1, 1, true);
        tips(new int[] {1, 1});

        UpsertResult result = manager.upsert(update(MatchStatus.FINISHED, 2, 3));

        assertEquals(Outcome.MANUALLY_CORRECTED, result.outcome());
        assertFalse(result.resultChanged());
        verify(updateStmt, never()).executeUpdate();
        verify(insertStmt, never()).executeQuery();
        // gewertet mit 1:1 aus der Tabelle, nicht mit 2:3 aus dem Event
        verify(tipPointsStmt).setInt(1, 4);
        assertEquals(1, result.tipsScored());
    }

    @Test
    void databaseErrorRollsBackMatchAndPoints() throws SQLException {
        existingRow(MatchStatus.UPCOMING, null, null, false);
        tips(new int[] {2, 3});
        when(tipPointsStmt.executeBatch()).thenThrow(new SQLException("connection lost"));

        assertThrows(IllegalStateException.class, () -> manager.upsert(update(MatchStatus.FINISHED, 2, 3)));

        verify(updateStmt).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(clearPointsStmt, never()).executeUpdate();
    }
}
