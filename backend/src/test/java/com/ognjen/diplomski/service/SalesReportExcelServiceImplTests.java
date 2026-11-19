package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.GratisProductReportResponse;
import com.ognjen.diplomski.dto.GratisReasonReportResponse;
import com.ognjen.diplomski.dto.GratisReportSummaryResponse;
import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.dto.MonthlyIncentiveResponse;
import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.ProductSalesReportResponse;
import com.ognjen.diplomski.dto.SalesReportSummaryResponse;
import com.ognjen.diplomski.dto.YtdSalesReportResponse;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.service.impl.SalesReportExcelServiceImpl;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesReportExcelServiceImplTests {

    private final SalesReportExcelService service = new SalesReportExcelServiceImpl();

    @Test
    void monthlyWorkbookContainsSummaryAndNumericProductData() throws Exception {
        MonthlySalesReportResponse report = new MonthlySalesReportResponse(
                2026,
                9,
                null,
                summary(),
                List.of(product())
        );

        byte[] bytes = service.createMonthlyWorkbook(report, "Petar", "Petrović");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, workbook.getNumberOfSheets());
            var sheet = workbook.getSheet("Mesečni izveštaj");
            assertEquals("Mesečni izveštaj prodaje", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("2026-09", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("Petar Petrović", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals(12, sheet.getRow(5).getCell(1).getNumericCellValue());
            assertEquals("BOTTLE", sheet.getRow(12).getCell(2).getStringCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(12).getCell(3).getCellType());
            assertEquals(12, sheet.getRow(12).getCell(3).getNumericCellValue());
            assertEquals(540, sheet.getRow(12).getCell(6).getNumericCellValue());
        }
    }

    @Test
    void ytdWorkbookContainsMonthlyChartSourceData() throws Exception {
        YtdSalesReportResponse report = new YtdSalesReportResponse(
                2026,
                null,
                summary(),
                List.of(product()),
                List.of(new MonthlyIncentiveResponse(1, new BigDecimal("120.50")))
        );

        byte[] bytes = service.createYtdWorkbook(report, "Petar", "Petrović");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals(
                    "Petar Petrović",
                    workbook.getSheet("YTD izveštaj").getRow(3).getCell(1).getStringCellValue()
            );
            var sheet = workbook.getSheet("Mesečna stimulacija");
            assertEquals(1, sheet.getRow(3).getCell(0).getNumericCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(3).getCell(1).getCellType());
            assertEquals(120.50, sheet.getRow(3).getCell(1).getNumericCellValue());
        }
    }

    @Test
    void gratisYtdWorkbookContainsGiftedPackageTotalsAndReasonLiters() throws Exception {
        GratisYtdReportResponse report = new GratisYtdReportResponse(
                2026,
                new GratisReportSummaryResponse(
                        new BigDecimal("5"),
                        new BigDecimal("2"),
                        new BigDecimal("3"),
                        new BigDecimal("16.500")
                ),
                List.of(
                        new GratisProductReportResponse(
                                "PR-001", "Boca", "products/placeholder.jpg",
                                new BigDecimal("2"), new BigDecimal("0.750"), PackageType.BOTTLE
                        ),
                        new GratisProductReportResponse(
                                "PR-002", "Balon", "products/placeholder.jpg",
                                new BigDecimal("3"), new BigDecimal("5.000"), PackageType.JUG
                        )
                ),
                List.of(new GratisReasonReportResponse(GratisReason.GIFT, new BigDecimal("16.500")))
        );

        byte[] bytes = service.createGratisYtdWorkbook(report, "Petar", "Petrović");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            var reportSheet = workbook.getSheet("Gratis YTD izveštaj");
            assertEquals("Petar Petrović", reportSheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals(2, reportSheet.getRow(5).getCell(1).getNumericCellValue());
            assertEquals(3, reportSheet.getRow(6).getCell(1).getNumericCellValue());
            assertEquals(16.5, reportSheet.getRow(7).getCell(1).getNumericCellValue());
            assertEquals("JUG", reportSheet.getRow(11).getCell(2).getStringCellValue());
            assertEquals(CellType.NUMERIC, reportSheet.getRow(11).getCell(3).getCellType());
            assertEquals(3, reportSheet.getRow(11).getCell(3).getNumericCellValue());
            var reasons = workbook.getSheet("Gratis po razlogu");
            assertEquals("Poklon", reasons.getRow(3).getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, reasons.getRow(3).getCell(1).getCellType());
            assertEquals(16.5, reasons.getRow(3).getCell(1).getNumericCellValue());
        }
    }

    @Test
    void monthlyGratisWorkbookContainsSelectedPeriodAndGiftedProducts() throws Exception {
        MonthlyGratisReportResponse report = new MonthlyGratisReportResponse(
                2026,
                6,
                new GratisReportSummaryResponse(
                        new BigDecimal("5"),
                        new BigDecimal("2"),
                        new BigDecimal("3"),
                        new BigDecimal("16.500")
                ),
                List.of(new GratisProductReportResponse(
                        "PR-001", "Boca", "products/placeholder.jpg",
                        new BigDecimal("2"), new BigDecimal("0.750"), PackageType.BOTTLE
                )),
                List.of(new GratisReasonReportResponse(GratisReason.GIFT, new BigDecimal("1.500")))
        );

        byte[] bytes = service.createMonthlyGratisWorkbook(report, "Petar", "Petrović");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            var reportSheet = workbook.getSheet("Mesečni gratis izveštaj");
            assertEquals("2026-06", reportSheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("Petar Petrović", reportSheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals(2, reportSheet.getRow(5).getCell(1).getNumericCellValue());
            assertEquals("BOTTLE", reportSheet.getRow(10).getCell(2).getStringCellValue());
            assertEquals(CellType.NUMERIC, reportSheet.getRow(10).getCell(3).getCellType());
        }
    }

    private static SalesReportSummaryResponse summary() {
        return new SalesReportSummaryResponse(
                new BigDecimal("12"),
                new BigDecimal("12"),
                BigDecimal.ZERO,
                new BigDecimal("9.000"),
                new BigDecimal("540.00")
        );
    }

    private static ProductSalesReportResponse product() {
        return new ProductSalesReportResponse(
                "PR-001",
                "Proizvod",
                "products/placeholder.jpg",
                new BigDecimal("12"),
                new BigDecimal("0.750"),
                PackageType.BOTTLE,
                new BigDecimal("45.00"),
                new BigDecimal("540.00")
        );
    }
}
