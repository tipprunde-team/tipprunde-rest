package com.mosbach.tipprunde.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mosbach.tipprunde.error.ApiException;

// Datenbank wird mit Mockito ersetzt, die Tests laufen also auch in der CI ohne aiven
class GroupAccessTest {

    private static final Instant JOINED = Instant.parse("2026-10-19T18:00:00Z");

    private final DataSource dataSource = mock(DataSource.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet resultSet = mock(ResultSet.class);
    private final GroupAccess groupAccess = new GroupAccess(dataSource);

    GroupAccessTest() throws SQLException {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
    }

    // Zeile der Abfrage: Gruppe existiert, role ist null wenn der Nutzer nicht Mitglied ist
    private void groupExists(String role) throws SQLException {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("role")).thenReturn(role);
        when(resultSet.getTimestamp("joined_at")).thenReturn(role == null ? null : Timestamp.from(JOINED));
    }

    private static ApiException thrownBy(Runnable action) {
        return assertThrows(ApiException.class, action::run);
    }

    @Test
    void unknownGroupReturns404GroupNotFound() throws SQLException {
        when(resultSet.next()).thenReturn(false);

        ApiException e = thrownBy(() -> groupAccess.requireMember(99, 7));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("GROUP_NOT_FOUND", e.getCode());
    }

    @Test
    void foreignGroupReturns403NotAMember() throws SQLException {
        groupExists(null);

        ApiException e = thrownBy(() -> groupAccess.requireMember(3, 7));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("NOT_A_MEMBER", e.getCode());
    }

    @Test
    void memberGetsMembershipWithRoleAndJoinDate() throws SQLException {
        groupExists("MEMBER");

        Membership membership = groupAccess.requireMember(3, 7);

        assertEquals(new Membership(3, 7, "MEMBER", JOINED), membership);
    }

    @Test
    void userIdAndGroupIdAreBoundAsParameters() throws SQLException {
        groupExists("ADMIN");

        groupAccess.requireMember(3, 7);

        verify(statement).setLong(1, 7);
        verify(statement).setLong(2, 3);
    }

    @Test
    void adminPassesRequireAdmin() throws SQLException {
        groupExists("ADMIN");

        Membership membership = groupAccess.requireAdmin(3, 7);

        assertEquals("ADMIN", membership.role());
    }

    @Test
    void normalMemberFailsRequireAdminWith403NotGroupAdmin() throws SQLException {
        groupExists("MEMBER");

        ApiException e = thrownBy(() -> groupAccess.requireAdmin(3, 7));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("NOT_GROUP_ADMIN", e.getCode());
    }

    @Test
    void requireAdminKeepsTheMemberErrors() throws SQLException {
        when(resultSet.next()).thenReturn(false);
        assertEquals("GROUP_NOT_FOUND", thrownBy(() -> groupAccess.requireAdmin(99, 7)).getCode());

        groupExists(null);
        assertEquals("NOT_A_MEMBER", thrownBy(() -> groupAccess.requireAdmin(3, 7)).getCode());
    }

    @Test
    void databaseErrorBecomesIllegalState() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("weg"));

        assertThrows(IllegalStateException.class, () -> groupAccess.requireMember(3, 7));
    }
}
