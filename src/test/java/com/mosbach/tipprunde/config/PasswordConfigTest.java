package com.mosbach.tipprunde.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

	private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

	@Test
	void hashIsNotPlainTextAndMatchesOnlyCorrectPassword() {
		String hash = encoder.encode("Test1234!");

		assertTrue(hash.startsWith("$2"));
		assertNotEquals("Test1234!", hash);
		assertTrue(encoder.matches("Test1234!", hash));
		assertFalse(encoder.matches("falsch", hash));
	}

	@Test
	void sameKeywordGetsDifferentHashesBecauseOfSalt() {
		assertNotEquals(encoder.encode("Test1234!"), encoder.encode("Test1234!"));
	}
}
