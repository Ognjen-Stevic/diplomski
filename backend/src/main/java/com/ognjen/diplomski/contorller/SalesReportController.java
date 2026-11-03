package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.dto.ManagersSalesComparisonResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.TeamSalesComparisonResponse;
import com.ognjen.diplomski.dto.YtdSalesReportResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.ReportSubjectResolver;
import com.ognjen.diplomski.service.SalesReportExcelService;
import com.ognjen.diplomski.service.SalesReportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales-reports")
@RequiredArgsConstructor
@Validated
public class SalesReportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final SalesReportService salesReportService;
    private final SalesReportExcelService salesReportExcelService;
    private final ReportSubjectResolver reportSubjectResolver;

    @GetMapping("/monthly")
    public MonthlySalesReportResponse getMonthlyReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        return salesReportService.getMonthlyReport(principal, year, month, salesRepId, managerId);
    }

    @GetMapping("/ytd")
    public YtdSalesReportResponse getYtdReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        return salesReportService.getYtdReport(principal, year, salesRepId, managerId);
    }

    @GetMapping("/team-comparison")
    public TeamSalesComparisonResponse getTeamComparison(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long managerId
    ) {
        return salesReportService.getTeamComparison(principal, year, month, managerId);
    }

    @GetMapping("/managers-comparison")
    public ManagersSalesComparisonResponse getManagersComparison(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month
    ) {
        return salesReportService.getManagersComparison(principal, year, month);
    }

    @GetMapping(value = "/monthly/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportMonthlyReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        MonthlySalesReportResponse report = salesReportService.getMonthlyReport(principal, year, month, salesRepId, managerId);
        String reportOwner = reportSubjectResolver.resolve(principal, salesRepId, managerId);
        byte[] workbook = salesReportExcelService.createMonthlyWorkbook(
                report,
                reportOwner,
                ""
        );
        String filename = "%s-monthly-report-%04d-%02d.xlsx".formatted(
                filenameName(reportOwner),
                year,
                month
        );
        return excelResponse(workbook, filename);
    }

    @GetMapping(value = "/ytd/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportYtdReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        YtdSalesReportResponse report = salesReportService.getYtdReport(principal, year, salesRepId, managerId);
        String reportOwner = reportSubjectResolver.resolve(principal, salesRepId, managerId);
        byte[] workbook = salesReportExcelService.createYtdWorkbook(
                report,
                reportOwner,
                ""
        );
        return excelResponse(
                workbook,
                "%s-ytd-report-%d.xlsx".formatted(filenameName(reportOwner), year)
        );
    }

    @GetMapping("/gratis-ytd")
    public GratisYtdReportResponse getGratisYtdReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        return salesReportService.getGratisYtdReport(principal, year, salesRepId, managerId);
    }

    @GetMapping("/gratis-monthly")
    public MonthlyGratisReportResponse getMonthlyGratisReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        return salesReportService.getMonthlyGratisReport(
                principal, year, month, salesRepId, managerId
        );
    }

    @GetMapping(value = "/gratis-monthly/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportMonthlyGratisReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        MonthlyGratisReportResponse report = salesReportService.getMonthlyGratisReport(
                principal, year, month, salesRepId, managerId
        );
        String reportOwner = reportSubjectResolver.resolve(principal, salesRepId, managerId);
        byte[] workbook = salesReportExcelService.createMonthlyGratisWorkbook(
                report,
                reportOwner,
                ""
        );
        return excelResponse(
                workbook,
                "%s-monthly-gratis-report-%04d-%02d.xlsx".formatted(
                        filenameName(reportOwner),
                        year,
                        month
                )
        );
    }

    @GetMapping(value = "/gratis-ytd/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportGratisYtdReport(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long managerId
    ) {
        GratisYtdReportResponse report = salesReportService.getGratisYtdReport(
                principal, year, salesRepId, managerId
        );
        String reportOwner = reportSubjectResolver.resolve(principal, salesRepId, managerId);
        byte[] workbook = salesReportExcelService.createGratisYtdWorkbook(
                report,
                reportOwner,
                ""
        );
        return excelResponse(
                workbook,
                "%s-gratis-ytd-report-%d.xlsx".formatted(filenameName(reportOwner), year)
        );
    }

    private static ResponseEntity<byte[]> excelResponse(byte[] workbook, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(workbook.length)
                .body(workbook);
    }

    private static String filenameName(String name) {
        return name
                .trim()
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-|-$", "");
    }

}
