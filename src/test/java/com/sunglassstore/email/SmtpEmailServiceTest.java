package com.sunglassstore.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SmtpEmailServiceTest {

    @Test
    void smtpFailureDoesNotEscapeIntoCommittedBusinessFlow() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
        SmtpEmailService service = new SmtpEmailService(sender);

        assertThrows(EmailDeliveryException.class, () -> service.send(new EmailMessage(
                "customer@example.com", "Refund completed", "Refund details")));
    }

    @Test
    void malformedOrMissingMessageDoesNotEscapeIntoCommittedBusinessFlow() {
        SmtpEmailService service = new SmtpEmailService(mock(JavaMailSender.class));

        assertThrows(EmailDeliveryException.class, () -> service.send(null));
    }
}
