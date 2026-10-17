package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.YtdSalesReportResponse;

public interface SalesReportExcelService {

    byte[] createMonthlyWorkbook(MonthlySalesReportResponse report, String firstName, String lastName);

    byte[] createYtdWorkbook(YtdSalesReportResponse report, String firstName, String lastName);

    byte[] createMonthlyGratisWorkbook(MonthlyGratisReportResponse report, String firstName, String lastName);

    byte[] createGratisYtdWorkbook(GratisYtdReportResponse report, String firstName, String lastName);
}
