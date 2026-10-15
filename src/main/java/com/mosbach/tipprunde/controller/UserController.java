package com.mosbach.tipprunde.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.UserManager;
import com.mosbach.tipprunde.model.user.RegisterRequest;
import com.mosbach.tipprunde.model.user.UserProfileResponse;
import com.mosbach.tipprunde.model.user.UserResponse;
import com.mosbach.tipprunde.security.CurrentUserId;

import jakarta.validation.Valid;

// Registrierung (ohne Token) und eigenes Profil (mit Token) unter /api/users
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManager userManager;

    public UserController(UserManager userManager) {
        this.userManager = userManager;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userManager.register(request);
    }

    // Die Nutzer-ID kommt aus dem geprüften Token, nie aus der URL
    @GetMapping("/me")
    public UserProfileResponse me(@CurrentUserId long userId) {
        return userManager.getProfile(userId);
    }
}
