package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.service.RequisitionPdfService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class RequisitionPdfServiceImpl implements RequisitionPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String FONT_RESOURCE = "/fonts/NotoSans-Regular.ttf";
    private static final float[] TABLE_WIDTHS = {72, 178, 72, 88, 105};
    private static final String[] TABLE_HEADERS = {"Šifra", "Proizvod", "Količina", "Cena", "Iznos"};

    @Override
    public byte[] createPdf(Requisition requisition) {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = RequisitionPdfServiceImpl.class.getResourceAsStream(FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new IllegalStateException("PDF font resource is missing");
            }

            PDFont font = PDType0Font.load(document, fontStream);
            try (PdfWriter writer = new PdfWriter(document, font)) {
                writeHeader(writer, requisition);
                writeItems(writer, requisition);
                writeSummaryAndSignatures(writer, requisition);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("PDF dokument nije moguće napraviti", exception);
        }
    }

    private static void writeHeader(PdfWriter writer, Requisition requisition) throws IOException {
        writer.text("TREBOVANJE", 20, 0);
        writer.text("Broj dokumenta: " + requisition.getId(), 11, 16);
        writer.text("Datum: " + requisition.getCreatedAt().format(DATE_FORMAT), 10, 4);
        writer.text(
                "Komercijalista: " + requisition.getSalesRep().getFirstName()
                        + " " + requisition.getSalesRep().getLastName(),
                10,
                4
        );
        writer.text(
                "Vrsta trebovanja: " + (requisition.getType() == RequisitionType.GRATIS ? "Gratis" : "Klasično"),
                10,
                14
        );

        writer.text("Kupac", 14, 7);
        writer.text("Naziv objekta: " + requisition.getCustomer().getObjectName(), 10, 4);
        writer.text("Naziv firme: " + requisition.getCustomer().getCompanyName(), 10, 4);
        writer.text("PIB: " + requisition.getCustomer().getPib(), 10, 4);
        writer.text("Adresa: " + requisition.getCustomer().getAddress(), 10, 16);
    }

    private static void writeItems(PdfWriter writer, Requisition requisition) throws IOException {
        writer.text("Stavke", 14, 7);
        writer.tableHeader();

        for (RequisitionItem item : requisition.getItems()) {
            BigDecimal lineTotal = item.getProduct().getPrice().multiply(item.getQuantity());
            writer.tableRow(new String[] {
                    item.getProduct().getCode(),
                    item.getProduct().getName(),
                    quantity(item.getQuantity()),
                    money(item.getProduct().getPrice()),
                    money(lineTotal)
            });
        }
    }

    private static void writeSummaryAndSignatures(PdfWriter writer, Requisition requisition) throws IOException {
        BigDecimal total = requisition.getItems().stream()
                .map(item -> item.getProduct().getPrice().multiply(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        writer.ensureSpace(133, false);
        writer.space(18);
        writer.textRight("Ukupna cena: " + money(total), 13, 18);
        writer.signatureLines();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " RSD";
    }

    private static String quantity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static final class PdfWriter implements AutoCloseable {
        private static final float MARGIN = 40;
        private static final float BOTTOM_MARGIN = 45;
        private static final float TABLE_FONT_SIZE = 8.5f;
        private static final float TABLE_LINE_HEIGHT = 11;
        private static final float CELL_PADDING = 4;

        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream content;
        private float y;

        private PdfWriter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void text(String value, float size, float spacingAfter) throws IOException {
            List<String> lines = wrap(value, PDRectangle.A4.getWidth() - 2 * MARGIN, size);
            for (String line : lines) {
                ensureSpace(size + 4, false);
                drawText(line, MARGIN, y, size);
                y -= size + 4;
            }
            y -= spacingAfter;
        }

        private void textRight(String value, float size, float spacingAfter) throws IOException {
            ensureSpace(size + spacingAfter, false);
            float width = font.getStringWidth(value) / 1000 * size;
            drawText(value, PDRectangle.A4.getWidth() - MARGIN - width, y, size);
            y -= size + spacingAfter;
        }

        private void space(float height) {
            y -= height;
        }

        private void tableHeader() throws IOException {
            ensureSpace(24, false);
            content.setNonStrokingColor(new Color(228, 235, 242));
            content.addRect(MARGIN, y - 22, tableWidth(), 22);
            content.fill();
            drawTableCells(TABLE_HEADERS, 22, true);
        }

        private void tableRow(String[] values) throws IOException {
            List<List<String>> wrapped = new ArrayList<>();
            int maximumLines = 1;
            for (int index = 0; index < values.length; index++) {
                List<String> lines = wrap(
                        values[index],
                        TABLE_WIDTHS[index] - 2 * CELL_PADDING,
                        TABLE_FONT_SIZE
                );
                wrapped.add(lines);
                maximumLines = Math.max(maximumLines, lines.size());
            }
            float height = maximumLines * TABLE_LINE_HEIGHT + 2 * CELL_PADDING;
            if (y - height < BOTTOM_MARGIN) {
                newPage();
                drawText("Trebovanje - nastavak", MARGIN, y, 11);
                y -= 20;
                tableHeader();
            }

            float x = MARGIN;
            content.setStrokingColor(new Color(170, 180, 190));
            for (int index = 0; index < TABLE_WIDTHS.length; index++) {
                content.addRect(x, y - height, TABLE_WIDTHS[index], height);
                content.stroke();
                float lineY = y - CELL_PADDING - TABLE_FONT_SIZE;
                for (String line : wrapped.get(index)) {
                    drawText(line, x + CELL_PADDING, lineY, TABLE_FONT_SIZE);
                    lineY -= TABLE_LINE_HEIGHT;
                }
                x += TABLE_WIDTHS[index];
            }
            y -= height;
        }

        private void drawTableCells(String[] values, float height, boolean header) throws IOException {
            float x = MARGIN;
            content.setStrokingColor(new Color(135, 150, 165));
            for (int index = 0; index < TABLE_WIDTHS.length; index++) {
                content.addRect(x, y - height, TABLE_WIDTHS[index], height);
                content.stroke();
                drawText(values[index], x + CELL_PADDING, y - 15, header ? 9 : TABLE_FONT_SIZE);
                x += TABLE_WIDTHS[index];
            }
            y -= height;
        }

        private void signatureLines() throws IOException {
            ensureSpace(70, false);
            float leftX = MARGIN;
            float rightX = PDRectangle.A4.getWidth() / 2 + 25;
            float width = 200;
            float lineY = y - 30;
            content.setStrokingColor(Color.DARK_GRAY);
            content.moveTo(leftX, lineY);
            content.lineTo(leftX + width, lineY);
            content.moveTo(rightX, lineY);
            content.lineTo(rightX + width, lineY);
            content.stroke();
            drawText("Potpis komercijaliste", leftX + 38, lineY - 15, 9);
            drawText("Potpis kupca", rightX + 64, lineY - 15, 9);
            y = lineY - 30;
        }

        private void ensureSpace(float height, boolean repeatTableHeader) throws IOException {
            if (y - height >= BOTTOM_MARGIN) return;
            newPage();
            if (repeatTableHeader) tableHeader();
        }

        private List<String> wrap(String value, float maximumWidth, float size) throws IOException {
            String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
            if (normalized.isEmpty()) return List.of("");

            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : normalized.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size <= maximumWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (!line.isEmpty()) lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (!line.isEmpty()) lines.add(line.toString());
            return lines;
        }

        private void drawText(String value, float x, float textY, float size) throws IOException {
            content.setNonStrokingColor(Color.BLACK);
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, textY);
            content.showText(value);
            content.endText();
        }

        private static float tableWidth() {
            float width = 0;
            for (float columnWidth : TABLE_WIDTHS) width += columnWidth;
            return width;
        }

        @Override
        public void close() throws IOException {
            if (content != null) content.close();
        }
    }
}
