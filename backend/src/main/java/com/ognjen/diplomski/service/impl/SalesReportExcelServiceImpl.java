package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.GratisProductReportResponse;
import com.ognjen.diplomski.dto.GratisReasonReportResponse;
import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.dto.GratisReportSummaryResponse;
import com.ognjen.diplomski.dto.MonthlyIncentiveResponse;
import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.ProductSalesReportResponse;
import com.ognjen.diplomski.dto.SalesReportSummaryResponse;
import com.ognjen.diplomski.dto.YtdSalesReportResponse;
import com.ognjen.diplomski.service.SalesReportExcelService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SalesReportExcelServiceImpl implements SalesReportExcelService {

    private static final String[] PRODUCT_HEADERS = {
            "Šifra", "Proizvod", "Vrsta pakovanja", "Prodato komada",
            "Litara po artiklu", "Stimulacija po artiklu", "Ukupna stimulacija"
    };
    private static final String[] GRATIS_PRODUCT_HEADERS = {
            "Šifra", "Proizvod", "Vrsta pakovanja", "Poklonjeno komada", "Litara po artiklu"
    };

    @Override
    public byte[] createMonthlyWorkbook(
            MonthlySalesReportResponse report,
            String firstName,
            String lastName
    ) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet("Mesečni izveštaj");
            String period = "%04d-%02d".formatted(report.year(), report.month());
            writeReportSheet(
                    sheet,
                    "Mesečni izveštaj prodaje",
                    period,
                    fullName(firstName, lastName),
                    report.summary(),
                    report.products(),
                    styles
            );
            return write(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException("Excel izveštaj nije moguće napraviti", exception);
        }
    }

    @Override
    public byte[] createYtdWorkbook(YtdSalesReportResponse report, String firstName, String lastName) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet reportSheet = workbook.createSheet("YTD izveštaj");
            LocalDate today = LocalDate.now();
            LocalDate reportEnd = report.year() == today.getYear()
                    ? today
                    : LocalDate.of(report.year(), 12, 31);
            writeReportSheet(
                    reportSheet,
                    "YTD izveštaj prodaje",
                    "01.01.%d - %s".formatted(
                            report.year(),
                            reportEnd.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    ),
                    fullName(firstName, lastName),
                    report.summary(),
                    report.products(),
                    styles
            );
            writeMonthlyIncentives(workbook.createSheet("Mesečna stimulacija"), report, styles);
            return write(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException("Excel izveštaj nije moguće napraviti", exception);
        }
    }

    @Override
    public byte[] createMonthlyGratisWorkbook(
            MonthlyGratisReportResponse report,
            String firstName,
            String lastName
    ) {
        return createGratisWorkbook(
                "Mesečni gratis izveštaj",
                "%04d-%02d".formatted(report.year(), report.month()),
                "Gratis po razlogu - %04d-%02d".formatted(report.year(), report.month()),
                report.summary(),
                report.products(),
                report.reasons(),
                fullName(firstName, lastName)
        );
    }

    @Override
    public byte[] createGratisYtdWorkbook(GratisYtdReportResponse report, String firstName, String lastName) {
        LocalDate today = LocalDate.now();
        LocalDate reportEnd = report.year() == today.getYear()
                ? today
                : LocalDate.of(report.year(), 12, 31);
        return createGratisWorkbook(
                "Gratis YTD izveštaj",
                "01.01.%d - %s".formatted(
                        report.year(),
                        reportEnd.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                ),
                "Gratis po razlogu - %d".formatted(report.year()),
                report.summary(),
                report.products(),
                report.reasons(),
                fullName(firstName, lastName)
        );
    }

    private static byte[] createGratisWorkbook(
            String title,
            String period,
            String reasonsTitle,
            GratisReportSummaryResponse summary,
            List<GratisProductReportResponse> products,
            List<GratisReasonReportResponse> reasons,
            String salesRepName
    ) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            writeGratisReportSheet(
                    workbook.createSheet(title),
                    title,
                    period,
                    summary,
                    products,
                    salesRepName,
                    styles
            );
            writeGratisReasons(workbook.createSheet("Gratis po razlogu"), reasonsTitle, reasons, styles);
            return write(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException("Excel izveštaj nije moguće napraviti", exception);
        }
    }

    private static void writeGratisReportSheet(
            Sheet sheet,
            String title,
            String period,
            GratisReportSummaryResponse summary,
            List<GratisProductReportResponse> products,
            String salesRepName,
            Styles styles
    ) {
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, GRATIS_PRODUCT_HEADERS.length - 1));

        writeTextPair(sheet.createRow(2), "Period", period, styles);
        writeTextPair(sheet.createRow(3), "Izveštaj za", salesRepName, styles);
        writeNumericPair(sheet.createRow(5), "Poklonjeno boca", summary.totalBottles(), styles.quantity(), styles);
        writeNumericPair(sheet.createRow(6), "Poklonjeno balona", summary.totalJugs(), styles.quantity(), styles);
        writeNumericPair(sheet.createRow(7), "Poklonjeno litara", summary.totalLiters(), styles.liters(), styles);

        int headerIndex = 9;
        Row header = sheet.createRow(headerIndex);
        for (int column = 0; column < GRATIS_PRODUCT_HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(GRATIS_PRODUCT_HEADERS[column]);
            cell.setCellStyle(styles.header());
        }

        int rowIndex = headerIndex + 1;
        for (GratisProductReportResponse product : products) {
            Row row = sheet.createRow(rowIndex++);
            writeText(row, 0, product.productCode());
            writeText(row, 1, product.productName());
            writeText(row, 2, product.packageType().name());
            writeNumber(row, 3, product.units(), styles.quantity());
            writeNumber(row, 4, product.volumeLiters(), styles.liters());
        }

        sheet.createFreezePane(0, headerIndex + 1);
        autoSize(sheet, GRATIS_PRODUCT_HEADERS.length);
    }

    private static void writeGratisReasons(
            Sheet sheet,
            String title,
            List<GratisReasonReportResponse> reasons,
            Styles styles
    ) {
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        Row header = sheet.createRow(2);
        Cell reasonHeader = header.createCell(0);
        reasonHeader.setCellValue("Razlog");
        reasonHeader.setCellStyle(styles.header());
        Cell litersHeader = header.createCell(1);
        litersHeader.setCellValue("Poklonjeno litara");
        litersHeader.setCellStyle(styles.header());

        int rowIndex = 3;
        for (GratisReasonReportResponse reason : reasons) {
            Row row = sheet.createRow(rowIndex++);
            writeText(row, 0, switch (reason.reason()) {
                case LISTING -> "Listing stimulacija";
                case CONSUMPTION -> "Stimulacija potrošnje";
                case GIFT -> "Poklon";
                case OTHER -> "Ostalo";
            });
            writeNumber(row, 1, reason.totalLiters(), styles.liters());
        }

        sheet.createFreezePane(0, 3);
        autoSize(sheet, 2);
    }

    private static void writeReportSheet(
            Sheet sheet,
            String title,
            String period,
            String salesRepName,
            SalesReportSummaryResponse summary,
            List<ProductSalesReportResponse> products,
            Styles styles
    ) {
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, PRODUCT_HEADERS.length - 1));

        writeTextPair(sheet.createRow(2), "Period", period, styles);
        writeTextPair(sheet.createRow(3), "Izveštaj za", salesRepName, styles);
        writeNumericPair(sheet.createRow(5), "Ukupno prodato artikala", summary.totalItems(), styles.quantity(), styles);
        writeNumericPair(sheet.createRow(6), "Prodate boce", summary.totalBottles(), styles.quantity(), styles);
        writeNumericPair(sheet.createRow(7), "Prodati baloni", summary.totalJugs(), styles.quantity(), styles);
        writeNumericPair(sheet.createRow(8), "Ukupno prodato litara", summary.totalLiters(), styles.liters(), styles);
        writeNumericPair(sheet.createRow(9), "Ukupna stimulacija", summary.totalIncentive(), styles.money(), styles);

        boolean companyReport = summary.salesRepIncentive() != null;
        if (companyReport) {
            writeNumericPair(sheet.getRow(9), "Ukupna stimulacija kompanije", summary.totalIncentive(), styles.money(), styles);
            writeNumericPair(sheet.createRow(10), "Stimulacija komercijalista", summary.salesRepIncentive(), styles.money(), styles);
            writeNumericPair(sheet.createRow(11), "Stimulacija menadžera", summary.managerIncentive(), styles.money(), styles);
        }
        int headerIndex = companyReport ? 13 : 11;
        Row header = sheet.createRow(headerIndex);
        for (int column = 0; column < PRODUCT_HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(PRODUCT_HEADERS[column]);
            cell.setCellStyle(styles.header());
        }

        int rowIndex = headerIndex + 1;
        for (ProductSalesReportResponse product : products) {
            Row row = sheet.createRow(rowIndex++);
            writeText(row, 0, product.productCode());
            writeText(row, 1, product.productName());
            writeText(row, 2, product.packageType().name());
            writeNumber(row, 3, product.units(), styles.quantity());
            writeNumber(row, 4, product.volumeLiters(), styles.liters());
            writeNumber(row, 5, product.incentivePerItem(), styles.money());
            writeNumber(row, 6, product.totalIncentive(), styles.money());
        }

        sheet.createFreezePane(0, headerIndex + 1);
        autoSize(sheet, PRODUCT_HEADERS.length);
    }

    private static void writeMonthlyIncentives(Sheet sheet, YtdSalesReportResponse report, Styles styles) {
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Mesečna stimulacija - %d".formatted(report.year()));
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        Row header = sheet.createRow(2);
        Cell monthHeader = header.createCell(0);
        monthHeader.setCellValue("Mesec");
        monthHeader.setCellStyle(styles.header());
        Cell incentiveHeader = header.createCell(1);
        incentiveHeader.setCellValue("Ukupna stimulacija");
        incentiveHeader.setCellStyle(styles.header());

        int rowIndex = 3;
        for (MonthlyIncentiveResponse monthly : report.monthlyIncentives()) {
            Row row = sheet.createRow(rowIndex++);
            Cell month = row.createCell(0);
            month.setCellValue(monthly.month());
            month.setCellStyle(styles.integer());
            writeNumber(row, 1, monthly.totalIncentive(), styles.money());
        }

        sheet.createFreezePane(0, 3);
        autoSize(sheet, 2);
    }

    private static void writeTextPair(Row row, String label, String value, Styles styles) {
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label());
        writeText(row, 1, value);
    }

    private static void writeNumericPair(
            Row row,
            String label,
            BigDecimal value,
            CellStyle numberStyle,
            Styles styles
    ) {
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label());
        writeNumber(row, 1, value, numberStyle);
    }

    private static void writeText(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static void writeNumber(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static void autoSize(Sheet sheet, int columnCount) {
        for (int column = 0; column < columnCount; column++) {
            sheet.autoSizeColumn(column);
            int paddedWidth = Math.min(sheet.getColumnWidth(column) + 768, 15000);
            sheet.setColumnWidth(column, paddedWidth);
        }
    }

    private static byte[] write(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String fullName(String firstName, String lastName) {
        return (firstName + " " + lastName).trim();
    }

    private record Styles(
            CellStyle title,
            CellStyle header,
            CellStyle label,
            CellStyle integer,
            CellStyle quantity,
            CellStyle liters,
            CellStyle money
    ) {
        private Styles(Workbook workbook) {
            this(
                    titleStyle(workbook),
                    headerStyle(workbook),
                    labelStyle(workbook),
                    numberStyle(workbook, "0"),
                    numberStyle(workbook, "0.##"),
                    numberStyle(workbook, "0.000"),
                    numberStyle(workbook, "#,##0.00")
            );
        }

        private static CellStyle titleStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 16);
            style.setFont(font);
            return style;
        }

        private static CellStyle headerStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            return style;
        }

        private static CellStyle labelStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        }

        private static CellStyle numberStyle(Workbook workbook, String format) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            return style;
        }
    }
}
