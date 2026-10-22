package com.mosbach.tipprunde.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.GroupManager;
import com.mosbach.tipprunde.model.group.InvitePreview;

// Einladungsvorschau ohne Anmeldung (Ausnahme im AuthInterceptor), damit die Beitrittsseite vor dem Login etwas anzeigen kann
@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final GroupManager groupManager;

    public InviteController(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    // 404 INVITE_INVALID bei unbekanntem Code
    @GetMapping("/{inviteCode}")
    public InvitePreview preview(@PathVariable String inviteCode) {
        return groupManager.getInvitePreview(inviteCode.trim());
    }
}
