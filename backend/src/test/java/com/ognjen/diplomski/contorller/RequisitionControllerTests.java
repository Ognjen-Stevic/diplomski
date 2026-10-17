package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.RequisitionEmailRecipient;
import com.ognjen.diplomski.dto.RequisitionEmailRequest;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.RequisitionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequisitionControllerTests {

    @Test
    void emailEndpointDelegatesRecipientTypeWithoutAcceptingAnAddress() {
        RequisitionService service = mock(RequisitionService.class);
        AppUserPrincipal principal = mock(AppUserPrincipal.class);

        new RequisitionController(service).sendPdfByEmail(
                principal,
                42L,
                new RequisitionEmailRequest(RequisitionEmailRecipient.CUSTOMER)
        );

        verify(service).sendPdfByEmail(principal, 42L, RequisitionEmailRecipient.CUSTOMER);
    }

    @Test
    void pdfEndpointReturnsGeneratedPdfAsAttachment() {
        RequisitionService service = mock(RequisitionService.class);
        AppUserPrincipal principal = mock(AppUserPrincipal.class);
        byte[] pdf = {37, 80, 68, 70};
        when(service.generatePdf(principal, 42L)).thenReturn(pdf);

        var response = new RequisitionController(service).downloadPdf(principal, 42L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals(pdf.length, response.getHeaders().getContentLength());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("trebovanje-42.pdf"));
        assertArrayEquals(pdf, response.getBody());
        verify(service).generatePdf(principal, 42L);
    }
}
