package com.consoleadda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * All services read time from this {@link Clock} so tests can control "now".
 * The clock is UTC; business dates are derived with {@link AppProperties#timezone()}.
 * <p>
 * It ticks in whole microseconds to match MySQL {@code DATETIME(6)}: MySQL <em>rounds</em> extra precision,
 * so a nanosecond timestamp could be stored up to 0.5µs later than the value the code compared with
 * (e.g. 71 minutes played becoming 70.99999 and being billed one interval too low).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
