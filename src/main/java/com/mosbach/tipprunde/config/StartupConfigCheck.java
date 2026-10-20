package com.mosbach.tipprunde.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Checks the configuration right at startup, so a wrong DB_URL does not only show up at the first login
@Component
public class StartupConfigCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigCheck.class);

    private static final String JDBC_PREFIX = "jdbc:postgresql://";
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String jwtSecret;
    private final boolean connectionCheckEnabled;

    public StartupConfigCheck(DataSource dataSource,
            @Value("${spring.datasource.url:}") String dbUrl,
            @Value("${spring.datasource.username:}") String dbUser,
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.startup.db-check:true}") boolean connectionCheckEnabled) {
        this.dataSource = dataSource;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.jwtSecret = jwtSecret;
        this.connectionCheckEnabled = connectionCheckEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = findProblems(dbUrl, dbUser, dbPassword, jwtSecret);
        if (!problems.isEmpty()) {
            problems.forEach(problem -> log.error("Configuration error: {}", problem));
            throw new IllegalStateException("Invalid configuration, see the errors above. "
                    + "Fix the environment variables and open a new terminal (see README, section Fehlerbehebung).");
        }

        if (!connectionCheckEnabled) {
            log.info("Database connection check at startup is disabled");
            return;
        }
        checkConnection();
    }

    // Opens one connection, so network, password and IP filter problems show up at startup
    private void checkConnection() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new IllegalStateException("Database connection is not valid");
            }
            log.info("Database connection OK ({})", hostOf(dbUrl));
        } catch (SQLException e) {
            log.error("Database not reachable at {}: {}", hostOf(dbUrl), e.getMessage());
            log.error("Check DB_URL, DB_USER, DB_PASSWORD and whether aiven allows connections from your IP");
            throw new IllegalStateException("Database not reachable at startup", e);
        }
    }

    // Pure check without database, unit-tested; returns readable problems, never the secret values
    static List<String> findProblems(String url, String user, String password, String secret) {
        List<String> problems = new ArrayList<>();

        if (isBlank(url)) {
            problems.add("DB_URL is missing");
        } else if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            problems.add("DB_URL is the aiven service URI. Use jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require "
                    + "without user and password");
        } else if (!url.startsWith(JDBC_PREFIX)) {
            problems.add("DB_URL must start with " + JDBC_PREFIX);
        } else if (containsPlaceholder(url) || url.contains("HOST") || url.contains(":PORT")) {
            problems.add("DB_URL still contains the placeholder HOST or PORT from the README");
        } else if (url.contains("@")) {
            problems.add("DB_URL must not contain user or password, they belong in DB_USER and DB_PASSWORD");
        }

        if (isBlank(user)) {
            problems.add("DB_USER is missing");
        } else if (containsPlaceholder(user)) {
            problems.add("DB_USER still contains a placeholder");
        }

        if (isBlank(password)) {
            problems.add("DB_PASSWORD is missing");
        } else if (containsPlaceholder(password)) {
            problems.add("DB_PASSWORD still contains a placeholder");
        }

        if (!isBlank(secret) && containsPlaceholder(secret)) {
            problems.add("JWT_SECRET still contains a placeholder");
        }
        return problems;
    }

    // Placeholders in the README are written in angle brackets, e.g. <password>
    private static boolean containsPlaceholder(String value) {
        return value.contains("<") || value.contains(">");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Host and port only, never user or password
    static String hostOf(String url) {
        if (url == null || !url.startsWith(JDBC_PREFIX)) {
            return "unknown host";
        }
        String rest = url.substring(JDBC_PREFIX.length());
        int end = rest.indexOf('/');
        return end >= 0 ? rest.substring(0, end) : rest;
    }
}
