package com.ognjen.diplomski.domain.report;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportPeriodFactoryTests {

    private final ReportPeriodFactory factory = new ReportPeriodFactory();
    private final ZoneId zone = ZoneId.of("Europe/Budapest");

    @Test
    void monthlyPeriodUsesInclusiveMonthStartAndExclusiveNextMonthStart() {
        ReportPeriod period = factory.monthly(2026, 3, zone);

        assertEquals(OffsetDateTime.parse("2026-03-01T00:00:00+01:00"), period.start());
        assertEquals(OffsetDateTime.parse("2026-04-01T00:00:00+02:00"), period.end());
    }

    @Test
    void yearlyPeriodCoversTheWholeCalendarYear() {
        ReportPeriod period = factory.yearly(2025, zone);

        assertEquals(OffsetDateTime.parse("2025-01-01T00:00:00+01:00"), period.start());
        assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00+01:00"), period.end());
    }

    @Test
    void currentYearToDateEndsAtTheProvidedCurrentTime() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-14T16:00:00+01:00");

        ReportPeriod period = factory.yearToDate(2026, now, zone);

        assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00+01:00"), period.start());
        assertEquals(now, period.end());
    }
}
