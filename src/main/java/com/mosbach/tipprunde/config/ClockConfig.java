package com.mosbach.tipprunde.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// KI-Log AH-07 "Baue GET …/matches mit Sperre und Heroku-Setup."
// Single source of server time; tests use a fixed clock instead
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
