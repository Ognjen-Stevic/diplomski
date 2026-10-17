package com.ognjen.diplomski.service;

import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.service.impl.RequisitionEmailServiceImpl;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequisitionEmailServiceImplTests {

    @Test
    void generatedPdfIsAttachedToTheEmail() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        Requisition requisition = new Requisition();
        requisition.setId(42L);

        new RequisitionEmailServiceImpl(mailSender, "office@example.com")
                .sendPdf(requisition, "recipient@example.com", new byte[]{1, 2, 3});
        message.saveChanges();

        assertEquals("Trebovanje #42", message.getSubject());
        assertEquals("recipient@example.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("office@example.com", message.getFrom()[0].toString());
        assertTrue(message.getContentType().startsWith("multipart/mixed"));
        var multipart = (jakarta.mail.Multipart) message.getContent();
        assertEquals(2, multipart.getCount());
        assertEquals("trebovanje-42.pdf", multipart.getBodyPart(1).getFileName());
        assertTrue(multipart.getBodyPart(1).getContentType().startsWith("application/pdf"));
        verify(mailSender).send(message);
    }
}
