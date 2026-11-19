package com.ognjen.diplomski.domain.requisition;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

public final class RequisitionPeriodFactory {

    public RequisitionPeriod monthly(int year, int month) {
        return monthly(year, month, ZoneId.systemDefault());
    }

    public RequisitionPeriod monthly(int year, int month, ZoneId zone) {
        YearMonth period = YearMonth.of(year, month);
        return new RequisitionPeriod(
                period.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                period.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime()
        );
    }

    public record RequisitionPeriod(OffsetDateTime start, OffsetDateTime end) {
    }
}
