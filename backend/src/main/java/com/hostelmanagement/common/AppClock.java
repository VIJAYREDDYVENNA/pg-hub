package com.hostelmanagement.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Single source of "now" and business "today" (in the configured business time zone). */
@Component
public class AppClock {

    private final Clock clock;

    public AppClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}
