package com.consoleadda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * All services read time from this {@link Clock} so tests can control "now".
 * The clock is UTC; business dates are derived with {@link AppProperties#timezone()}.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
