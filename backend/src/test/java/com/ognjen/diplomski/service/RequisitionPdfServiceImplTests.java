package com.ognjen.diplomski.service;

import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.service.impl.RequisitionPdfServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequisitionPdfServiceImplTests {

    private final RequisitionPdfService service = new RequisitionPdfServiceImpl();

    @Test
    void pdfContainsRequiredDataTotalsAndSignaturesWithoutVat() throws Exception {
        Requisition requisition = requisition();
        addItem(requisition, "PR-ČĆ-1", "Čokoladno mleko", "3", "12.50");

        byte[] pdf = service.createPdf(requisition);

        assertTrue(pdf.length > 1000);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF-"));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Broj dokumenta: 42"));
            assertTrue(text.contains("Objekat Čačak"));
            assertTrue(text.contains("Preduzeće Žubor"));
            assertTrue(text.contains("109876543"));
            assertTrue(text.contains("Bulevar oslobođenja 10"));
            assertTrue(text.contains("Petar Petrović"));
            assertTrue(text.contains("Klasično"));
            assertTrue(text.contains("PR-ČĆ-1"));
            assertTrue(text.contains("Čokoladno mleko"));
            assertTrue(text.contains("37.50 RSD"));
            assertTrue(text.contains("Ukupna cena: 37.50 RSD"));
            assertTrue(text.contains("Potpis komercijaliste"));
            assertTrue(text.contains("Potpis kupca"));
            assertFalse(text.contains("PDV"));
            assertFalse(text.contains("VAT"));
        }
    }

    @Test
    void itemTableContinuesAcrossPages() throws Exception {
        Requisition requisition = requisition();
        for (int index = 1; index <= 55; index++) {
            addItem(
                    requisition,
                    "PR-" + index,
                    "Proizvod sa dužim nazivom broj " + index,
                    "2",
                    "5.00"
            );
        }

        byte[] pdf = service.createPdf(requisition);

        try (var document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("PR-55"));
            assertTrue(text.contains("Ukupna cena: 550.00 RSD"));
        }
    }

    private static Requisition requisition() {
        AppUser salesRepresentative = new AppUser();
        salesRepresentative.setId(20L);
        salesRepresentative.setFirstName("Petar");
        salesRepresentative.setLastName("Petrović");

        Customer customer = new Customer();
        customer.setId(100L);
        customer.setObjectName("Objekat Čačak");
        customer.setCompanyName("Preduzeće Žubor");
        customer.setPib("109876543");
        customer.setAddress("Bulevar oslobođenja 10");

        Requisition requisition = new Requisition();
        requisition.setId(42L);
        requisition.setSalesRep(salesRepresentative);
        requisition.setCustomer(customer);
        requisition.setType(RequisitionType.NORMAL);
        requisition.setStatus(RequisitionStatus.COMPLETED);
        requisition.setCreatedAt(OffsetDateTime.of(2026, 10, 7, 12, 30, 0, 0, ZoneOffset.UTC));
        return requisition;
    }

    private static void addItem(
            Requisition requisition,
            String code,
            String name,
            String quantity,
            String price
    ) {
        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setPrice(new BigDecimal(price));

        RequisitionItem item = new RequisitionItem();
        item.setRequisition(requisition);
        item.setProduct(product);
        item.setQuantity(new BigDecimal(quantity));
        requisition.getItems().add(item);
    }
}
