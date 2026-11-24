package com.jitong.core.common;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimeFormat {

    private static final DateTimeFormatter ISO_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneOffset.UTC);

    private TimeFormat() {
    }

    public static String iso(Instant instant) {
        return instant == null ? null : ISO_SECONDS.format(instant);
    }

    public static Instant nowSeconds(java.time.Clock clock) {
        return Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }
}
