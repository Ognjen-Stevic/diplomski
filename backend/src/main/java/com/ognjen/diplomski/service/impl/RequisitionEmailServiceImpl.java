package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.service.RequisitionEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class RequisitionEmailServiceImpl implements RequisitionEmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public RequisitionEmailServiceImpl(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPdf(Requisition requisition, String recipientEmail, byte[] pdf) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject("Trebovanje #%d".formatted(requisition.getId()));
            helper.setText(
                    "U prilogu se nalazi završeno trebovanje #%d.".formatted(requisition.getId()),
                    false
            );
            helper.addAttachment(
                    "trebovanje-%d.pdf".formatted(requisition.getId()),
                    new ByteArrayResource(pdf),
                    "application/pdf"
            );
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Requisition email could not be sent",
                    exception
            );
        }
    }
}
