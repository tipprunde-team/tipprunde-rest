package com.mosbach.tipprunde.data.api;

import com.mosbach.tipprunde.model.user.RegisterRequest;
import com.mosbach.tipprunde.model.user.UserProfileResponse;
import com.mosbach.tipprunde.model.user.UserResponse;

// Registrierung und Profil
public interface UserManager {

    // 400 VALIDATION_ERROR bei zu langem Passwort, 409 EMAIL_TAKEN bzw. USERNAME_TAKEN
    UserResponse register(RegisterRequest request);

    // 401 UNAUTHORIZED, wenn es den Nutzer nicht (mehr) gibt
    UserProfileResponse getProfile(long userId);
}
