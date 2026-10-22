package com.mosbach.tipprunde.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.data.api.GroupManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.group.CreateGroupRequest;
import com.mosbach.tipprunde.model.group.GroupCreated;
import com.mosbach.tipprunde.model.group.GroupDetails;
import com.mosbach.tipprunde.model.group.GroupSummary;
import com.mosbach.tipprunde.model.group.InvitePreview;
import com.mosbach.tipprunde.security.AuthInterceptor;
import com.mosbach.tipprunde.security.CurrentUserIdArgumentResolver;

// Standalone-Test mit echtem AuthInterceptor: die Vorschau muss ohne Token erreichbar sein
class InviteControllerTest {

    private final FakeGroupManager groupManager = new FakeGroupManager();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InviteController(groupManager))
            .addInterceptors(new AuthInterceptor(new FakeAuthManager()))
            .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void previewWorksWithoutToken() throws Exception {
        mockMvc.perform(get("/api/invites/k7Qp2xZ9aB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(3))
                .andExpect(jsonPath("$.groupName").value("WG Lohrtalweg"))
                .andExpect(jsonPath("$.memberCount").value(4));
        assertEquals("k7Qp2xZ9aB", groupManager.lastCode);
    }

    @Test
    void previewContainsNeitherMembersNorInviteCode() throws Exception {
        mockMvc.perform(get("/api/invites/k7Qp2xZ9aB"))
                .andExpect(jsonPath("$.members").doesNotExist())
                .andExpect(jsonPath("$.inviteCode").doesNotExist());
    }

    @Test
    void unknownCodeReturns404InviteInvalid() throws Exception {
        groupManager.failWith = new ApiException(HttpStatus.NOT_FOUND, "INVITE_INVALID", "Dieser Einladungscode ist ungültig.");

        mockMvc.perform(get("/api/invites/falsch"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_INVALID"))
                .andExpect(jsonPath("$.error.message").value("Dieser Einladungscode ist ungültig."));
    }

    @Test
    void previewIgnoresAnInvalidTokenBecauseTheEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/invites/k7Qp2xZ9aB").header("Authorization", "Bearer kaputt"))
                .andExpect(status().isOk());
    }

    // Merkt sich den letzten Code und wirft auf Wunsch einen Fehler
    private static class FakeGroupManager implements GroupManager {

        String lastCode;
        ApiException failWith;

        @Override
        public InvitePreview getInvitePreview(String inviteCode) {
            lastCode = inviteCode;
            if (failWith != null) {
                throw failWith;
            }
            return new InvitePreview(3, "WG Lohrtalweg", 4);
        }

        @Override
        public GroupCreated createGroup(long userId, CreateGroupRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GroupSummary> listGroups(long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroupDetails getGroupDetails(long groupId, boolean includeInviteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroupSummary joinGroup(long userId, String inviteCode) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeAuthManager implements AuthManager {

        @Override
        public TokenAnswer logOn(String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logOff(String token) {
        }

        @Override
        public Optional<Long> getUserIdFromToken(String token) {
            return Optional.empty();
        }
    }
}
