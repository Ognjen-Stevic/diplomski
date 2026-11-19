package com.ognjen.diplomski.domain.report;

import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;

public final class ReportPeriodFactory {

    public ReportPeriod monthly(int year, int month, ZoneId zone) {
        YearMonth period = YearMonth.of(year, month);
        return new ReportPeriod(
                period.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                period.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime()
        );
    }

    public ReportPeriod yearly(int year, ZoneId zone) {
        return new ReportPeriod(
                Year.of(year).atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                Year.of(year + 1).atDay(1).atStartOfDay(zone).toOffsetDateTime()
        );
    }

    public ReportPeriod yearToDate(int year, OffsetDateTime now, ZoneId zone) {
        ReportPeriod yearly = yearly(year, zone);
        return new ReportPeriod(yearly.start(), year == now.getYear() ? now : yearly.end());
    }
}
