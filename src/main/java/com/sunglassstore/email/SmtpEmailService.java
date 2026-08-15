package com.sunglassstore.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void send(EmailMessage email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
            message.setTo(email.to());
            message.setSubject(email.subject());
            message.setText(email.body());
            mailSender.send(message);
        } catch (MailException | IllegalArgumentException | NullPointerException exception) {
            String recipient = email == null ? "<missing>" : email.to();
            String subject = email == null ? "<missing>" : email.subject();
            log.error("Email delivery failed to {} for '{}': {}", recipient, subject, exception.getMessage());
            throw new EmailDeliveryException("Email delivery failed: " + exception.getMessage(), exception);
        }
    }
}
