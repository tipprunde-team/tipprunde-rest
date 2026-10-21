package com.mosbach.tipprunde.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

// Pure checks without Spring context and database, so they run in the CI
class StartupConfigCheckTest {

    private static final String VALID_URL = "jdbc:postgresql://pg-test.aivencloud.com:12345/defaultdb?sslmode=require";
    private static final String VALID_SECRET = "abcdefghijklmnopqrstuvwxyz0123456789ABCD";

    @Test
    void validConfigurationHasNoProblems() {
        assertThat(StartupConfigCheck.findProblems(VALID_URL, "avnadmin", "secret", VALID_SECRET)).isEmpty();
    }

    @Test
    void readmePlaceholderInUrlIsReported() {
        List<String> problems = StartupConfigCheck.findProblems(
                "jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require", "avnadmin", "secret", VALID_SECRET);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("placeholder");
    }

    @Test
    void angleBracketPlaceholderInUrlIsReported() {
        List<String> problems = StartupConfigCheck.findProblems(
                "jdbc:postgresql://<HOST>:<PORT>/defaultdb?sslmode=require", "avnadmin", "secret", VALID_SECRET);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("placeholder");
    }

    @Test
    void aivenServiceUriIsReportedWithHint() {
        List<String> problems = StartupConfigCheck.findProblems(
                "postgres://avnadmin:pw@pg-test.aivencloud.com:12345/defaultdb?sslmode=require",
                "avnadmin", "secret", VALID_SECRET);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("service URI");
    }

    @Test
    void urlWithCredentialsIsReported() {
        List<String> problems = StartupConfigCheck.findProblems(
                "jdbc:postgresql://avnadmin:pw@pg-test.aivencloud.com:12345/defaultdb",
                "avnadmin", "secret", VALID_SECRET);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("must not contain");
    }

    @Test
    void missingValuesAreReported() {
        List<String> problems = StartupConfigCheck.findProblems("", " ", null, VALID_SECRET);
        assertThat(problems).containsExactly("DB_URL is missing", "DB_USER is missing", "DB_PASSWORD is missing");
    }

    @Test
    void placeholdersInUserPasswordAndSecretAreReported() {
        List<String> problems = StartupConfigCheck.findProblems(VALID_URL, "<user>",
                "<Passwort aus der Privatnachricht>", "<gemeinsames Secret aus der Privatnachricht>");
        assertThat(problems).hasSize(3);
    }

    @Test
    void problemsNeverContainSecretValues() {
        List<String> problems = StartupConfigCheck.findProblems(VALID_URL, "avnadmin", "<topsecret>", VALID_SECRET);
        assertThat(problems).noneMatch(problem -> problem.contains("topsecret"));
    }

    @Test
    void hostOfReturnsHostAndPortOnly() {
        assertThat(StartupConfigCheck.hostOf(VALID_URL)).isEqualTo("pg-test.aivencloud.com:12345");
        assertThat(StartupConfigCheck.hostOf("postgres://x")).isEqualTo("unknown host");
    }
}
