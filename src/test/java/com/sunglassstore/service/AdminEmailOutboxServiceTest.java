package com.sunglassstore.service;

import com.sunglassstore.dto.response.EmailOutboxAdminResponse;
import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminEmailOutboxServiceTest {
    private EmailOutboxRepository repository;
    private AdminEmailOutboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailOutboxRepository.class);
        service = new AdminEmailOutboxService(repository);
    }

    @Test
    void failedNotificationCanBeQueuedForImmediateRetry() {
        EmailOutbox email = failed("Message body", null);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(email));
        when(repository.save(email)).thenReturn(email);

        EmailOutboxAdminResponse response = service.retry(7L);

        assertEquals(EmailOutboxStatus.RETRY, email.getStatus());
        assertEquals(0, email.getAttemptCount());
        assertNull(email.getLastError());
        assertFalse(response.canRetry());
        assertTrue(email.getNextAttemptAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void scrubbedMessageCannotBeRetried() {
        EmailOutbox email = failed("", null);
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(email));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.retry(8L));

        assertTrue(error.getMessage().contains("securely removed"));
        verify(repository, never()).save(any());
    }

    @Test
    void expiredMessageCannotBeRetriedEvenWhenPayloadExists() {
        EmailOutbox email = failed("Secret", LocalDateTime.now().minusMinutes(1));
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(email));

        assertThrows(BadRequestException.class, () -> service.retry(9L));
        verify(repository, never()).save(any());
    }

    @Test
    void responseDoesNotExposeEmailBody() {
        EmailOutbox email = failed("private reset link", null);
        EmailOutboxAdminResponse response = EmailOutboxAdminResponse.fromEntity(email, LocalDateTime.now());

        assertTrue(response.canRetry());
        assertFalse(java.util.Arrays.stream(EmailOutboxAdminResponse.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("body")));
    }

    private EmailOutbox failed(String body, LocalDateTime expiresAt) {
        EmailOutbox email = new EmailOutbox();
        email.setEmailOutboxId(7L);
        email.setRecipient("customer@example.com");
        email.setSubject("Order update");
        email.setBody(body);
        email.setStatus(EmailOutboxStatus.FAILED);
        email.setAttemptCount(8);
        email.setLastError("SMTP unavailable");
        email.setCreatedAt(LocalDateTime.now().minusHours(1));
        email.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        email.setExpiresAt(expiresAt);
        return email;
    }
}
