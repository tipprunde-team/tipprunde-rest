package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mosbach.tipprunde.model.ranking.RankingEntry;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
// Datenbank mit Mockito ersetzt: geprüft werden Parameter, SQL-Variante und die Rangvergabe auf den Ergebnissen
class PostgresRankingManagerImplTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet resultSet = mock(ResultSet.class);
    private final PostgresRankingManagerImpl manager = new PostgresRankingManagerImpl(dataSource);

    PostgresRankingManagerImplTest() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
    }

    // Zeilen als {userId, username, punkte, exakteTipps}
    private void rows(Object[]... rows) throws SQLException {
        Boolean[] next = new Boolean[rows.length];
        java.util.Arrays.fill(next, true);
        Boolean[] sequence = java.util.Arrays.copyOf(next, rows.length + 1);
        sequence[rows.length] = false;
        when(resultSet.next()).thenReturn(sequence[0], java.util.Arrays.copyOfRange(sequence, 1, sequence.length));
        Long[] ids = new Long[rows.length];
        String[] names = new String[rows.length];
        Integer[] points = new Integer[rows.length];
        Integer[] exact = new Integer[rows.length];
        for (int i = 0; i < rows.length; i++) {
            ids[i] = ((Number) rows[i][0]).longValue();
            names[i] = (String) rows[i][1];
            points[i] = (Integer) rows[i][2];
            exact[i] = (Integer) rows[i][3];
        }
        when(resultSet.getLong("user_id")).thenReturn(ids[0], java.util.Arrays.copyOfRange(ids, 1, ids.length));
        when(resultSet.getString("username")).thenReturn(names[0], java.util.Arrays.copyOfRange(names, 1, names.length));
        when(resultSet.getInt("pts")).thenReturn(points[0], java.util.Arrays.copyOfRange(points, 1, points.length));
        when(resultSet.getInt("exact_tips")).thenReturn(exact[0], java.util.Arrays.copyOfRange(exact, 1, exact.length));
    }

    @Test
    void ranksMembersFromTheDatabaseRowsWithTies() throws SQLException {
        rows(new Object[] { 30L, "arlind", 27, 2 }, new Object[] { 21L, "max", 31, 2 },
                new Object[] { 7L, "niklas", 31, 3 }, new Object[] { 12L, "maximilian", 35, 4 });

        List<RankingEntry> ranking = manager.getRanking(3, null);

        assertEquals(List.of(1, 2, 2, 4), ranking.stream().map(RankingEntry::rank).toList());
        assertEquals("maximilian", ranking.get(0).username());
        assertEquals(35, ranking.get(0).points());
        assertEquals(4, ranking.get(0).exactTips());
    }

    @Test
    void totalRankingBindsExactPointsAndGroupIdWithoutMatchdayFilter() throws SQLException {
        when(resultSet.next()).thenReturn(false);

        manager.getRanking(3, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertFalse(sql.getValue().contains("matchday"), sql.getValue());
        verify(statement).setInt(1, 4);
        verify(statement).setLong(2, 3);
    }

    @Test
    void matchdayRankingFiltersTipsByMatchdayAsParameter() throws SQLException {
        when(resultSet.next()).thenReturn(false);

        manager.getRanking(3, 6);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("matchday = ?"), sql.getValue());
        assertFalse(sql.getValue().contains("matchday = 6"), sql.getValue());
        verify(statement).setInt(1, 4);
        verify(statement).setInt(2, 6);
        verify(statement).setLong(3, 3);
    }

    @Test
    void membersWithoutTipsAreKeptViaLeftJoin() throws SQLException {
        when(resultSet.next()).thenReturn(false);

        manager.getRanking(3, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("LEFT JOIN tips"), sql.getValue());
        assertTrue(sql.getValue().contains("COALESCE(SUM(t.points), 0)"), sql.getValue());
    }

    @Test
    void groupWithoutRowsGivesEmptyRanking() throws SQLException {
        when(resultSet.next()).thenReturn(false);

        assertTrue(manager.getRanking(3, null).isEmpty());
    }

    @Test
    void databaseErrorBecomesIllegalState() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("weg"));

        assertThrows(IllegalStateException.class, () -> manager.getRanking(3, null));
    }
}
