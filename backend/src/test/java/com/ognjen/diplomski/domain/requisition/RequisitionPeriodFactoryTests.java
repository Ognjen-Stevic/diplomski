package com.ognjen.diplomski.domain.requisition;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequisitionPeriodFactoryTests {

    private final RequisitionPeriodFactory factory = new RequisitionPeriodFactory();

    @Test
    void monthlyPeriodUsesInclusiveStartAndExclusiveNextMonthStart() {
        var period = factory.monthly(2026, 9, ZoneId.of("Europe/Budapest"));

        assertEquals(OffsetDateTime.parse("2026-09-01T00:00:00+02:00"), period.start());
        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+02:00"), period.end());
    }

    @Test
    void monthlyPeriodPreservesDaylightSavingBoundaryBehavior() {
        var period = factory.monthly(2026, 3, ZoneId.of("Europe/Budapest"));

        assertEquals(OffsetDateTime.parse("2026-03-01T00:00:00+01:00"), period.start());
        assertEquals(OffsetDateTime.parse("2026-04-01T00:00:00+02:00"), period.end());
    }
}
