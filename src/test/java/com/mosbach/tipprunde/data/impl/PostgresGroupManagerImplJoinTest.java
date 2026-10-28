package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.group.GroupSummary;

// Ablauf des Beitritts mit gemockter Datenbank, läuft auch in der CI ohne aiven
class PostgresGroupManagerImplJoinTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement lockStmt = mock(PreparedStatement.class);
    private final PreparedStatement countStmt = mock(PreparedStatement.class);
    private final PreparedStatement insertStmt = mock(PreparedStatement.class);
    private final PreparedStatement listStmt = mock(PreparedStatement.class);
    private final ResultSet lockRs = mock(ResultSet.class);
    private final ResultSet countRs = mock(ResultSet.class);
    private final ResultSet listRs = mock(ResultSet.class);
    private final PostgresGroupManagerImpl manager;

    PostgresGroupManagerImplJoinTest() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("FOR UPDATE")))).thenReturn(lockStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("SELECT COUNT(*) AS member_count"))))
                .thenReturn(countStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("INSERT INTO group_members"))))
                .thenReturn(insertStmt);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("WITH my_groups")))).thenReturn(listStmt);
        when(lockStmt.executeQuery()).thenReturn(lockRs);
        when(countStmt.executeQuery()).thenReturn(countRs);
        when(listStmt.executeQuery()).thenReturn(listRs);
        manager = new PostgresGroupManagerImpl(dataSource, new InviteCodeGenerator());
    }

    // Gruppe 3 existiert, zählt memberCount Mitglieder, der Nutzer ist darunter oder nicht
    private void groupWith(int memberCount, boolean userIsMember) throws SQLException {
        when(lockRs.next()).thenReturn(true);
        when(lockRs.getLong("id")).thenReturn(3L);
        when(countRs.next()).thenReturn(true);
        when(countRs.getInt("member_count")).thenReturn(memberCount);
        when(countRs.getInt("is_member")).thenReturn(userIsMember ? 1 : 0);
    }

    private ApiException failedJoin() {
        return assertThrows(ApiException.class, () -> manager.joinGroup(7, "k7Qp2xZ9aB"));
    }

    @Test
    void successfulJoinInsertsMemberCommitsAndReturnsTheGroupEntry() throws SQLException {
        groupWith(4, false);
        when(listRs.next()).thenReturn(true, false);
        when(listRs.getLong("id")).thenReturn(3L);
        when(listRs.getString("name")).thenReturn("WG Lohrtalweg");
        when(listRs.getString("role")).thenReturn("MEMBER");
        when(listRs.getInt("member_count")).thenReturn(5);
        when(listRs.getInt("rnk")).thenReturn(5);
        when(listRs.getInt("pts")).thenReturn(0);

        GroupSummary result = manager.joinGroup(7, "k7Qp2xZ9aB");

        assertEquals(new GroupSummary(3, "WG Lohrtalweg", "MEMBER", 5, 5, 0), result);
        verify(lockStmt).setString(1, "k7Qp2xZ9aB");
        verify(insertStmt).setLong(1, 3);
        verify(insertStmt).setLong(2, 7);
        verify(insertStmt).executeUpdate();
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void unknownCodeReturns404InviteInvalidAndInsertsNothing() throws SQLException {
        when(lockRs.next()).thenReturn(false);

        ApiException e = failedJoin();

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("INVITE_INVALID", e.getCode());
        verify(insertStmt, never()).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void existingMemberGets409AlreadyMember() throws SQLException {
        groupWith(4, true);

        ApiException e = failedJoin();

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("ALREADY_MEMBER", e.getCode());
        verify(insertStmt, never()).executeUpdate();
        verify(connection).rollback();
    }

    @Test
    void existingMemberOfAFullGroupStillGetsAlreadyMember() throws SQLException {
        groupWith(200, true);

        assertEquals("ALREADY_MEMBER", failedJoin().getCode());
    }

    @Test
    void groupWith200MembersReturns409GroupFull() throws SQLException {
        groupWith(200, false);

        ApiException e = failedJoin();

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("GROUP_FULL", e.getCode());
        verify(insertStmt, never()).executeUpdate();
        verify(connection).rollback();
    }

    @Test
    void groupWith199MembersStillAcceptsTheNextOne() throws SQLException {
        groupWith(199, false);
        when(listRs.next()).thenReturn(true, false);
        when(listRs.getLong("id")).thenReturn(3L);

        manager.joinGroup(7, "k7Qp2xZ9aB");

        verify(insertStmt).executeUpdate();
        verify(connection).commit();
    }

    @Test
    void userThatNoLongerExistsGets401AndRollback() throws SQLException {
        groupWith(4, false);
        when(insertStmt.executeUpdate()).thenThrow(new SQLException("fk", "23503"));

        ApiException e = failedJoin();

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        verify(connection).rollback();
    }

    @Test
    void previewOfUnknownCodeReturns404InviteInvalid() throws SQLException {
        PreparedStatement previewStmt = mock(PreparedStatement.class);
        ResultSet previewRs = mock(ResultSet.class);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("SELECT g.id, g.name")))).thenReturn(previewStmt);
        when(previewStmt.executeQuery()).thenReturn(previewRs);
        when(previewRs.next()).thenReturn(false);

        ApiException e = assertThrows(ApiException.class, () -> manager.getInvitePreview("falsch"));

        assertEquals("INVITE_INVALID", e.getCode());
        verify(previewStmt).setString(1, "falsch");
    }

    @Test
    void previewOfKnownCodeReturnsGroupIdNameAndMemberCount() throws SQLException {
        PreparedStatement previewStmt = mock(PreparedStatement.class);
        ResultSet previewRs = mock(ResultSet.class);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.startsWith("SELECT g.id, g.name")))).thenReturn(previewStmt);
        when(previewStmt.executeQuery()).thenReturn(previewRs);
        when(previewRs.next()).thenReturn(true);
        when(previewRs.getLong("id")).thenReturn(3L);
        when(previewRs.getString("name")).thenReturn("WG Lohrtalweg");
        when(previewRs.getInt("member_count")).thenReturn(4);

        var preview = manager.getInvitePreview("k7Qp2xZ9aB");

        assertEquals(3L, preview.groupId());
        assertEquals("WG Lohrtalweg", preview.groupName());
        assertEquals(4, preview.memberCount());
    }
}
