package com.sunglassstore.email;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailOutboxService {
    private final EmailOutboxRepository repository;

    public void enqueue(EmailMessage message) {
        enqueue(message, null);
    }

    public void enqueue(EmailMessage message, LocalDateTime expiresAt) {
        EmailOutbox email = new EmailOutbox();
        email.setRecipient(message.to());
        email.setSubject(message.subject());
        email.setBody(message.body());
        email.setExpiresAt(expiresAt);
        repository.save(email);
    }
}
