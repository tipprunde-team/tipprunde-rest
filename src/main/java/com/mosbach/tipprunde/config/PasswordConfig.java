package com.mosbach.tipprunde.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// KI-Log MA-03 "Ergänze das Fehlerformat um die fehlenden Fälle und lege Schema und BCrypt-Bean an."
// BCrypt-Bean für Registrierung und Login (Passwörter nie im Klartext)
@Configuration
public class PasswordConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
