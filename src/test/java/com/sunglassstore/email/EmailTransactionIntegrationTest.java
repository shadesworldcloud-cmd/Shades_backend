package com.sunglassstore.email;

import com.sunglassstore.email.event.PasswordResetEmailRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sunglassstore.repository.EmailOutboxRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:emailoutboxtest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class EmailTransactionIntegrationTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EmailOutboxRepository outboxRepository;

    @BeforeEach
    void clearOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void notificationIsDeliveredOnlyAfterCommit() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(event());
            assertEquals(0, outboxRepository.count());
        });

        assertEquals(1, outboxRepository.count());
    }

    @Test
    void notificationIsDiscardedWhenBusinessTransactionRollsBack() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(event());
            status.setRollbackOnly();
        });

        assertEquals(0, outboxRepository.count());
    }

    private PasswordResetEmailRequested event() {
        return new PasswordResetEmailRequested("customer@example.com", "Customer", "token");
    }
}
