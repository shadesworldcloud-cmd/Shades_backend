package com.sunglassstore.controller;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminEmailOutboxControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private EmailOutboxRepository repository;

    @Test
    void anonymousUserCannotReadOutbox() throws Exception {
        mockMvc.perform(get("/api/admin/email-outbox"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void inventoryManagerCannotReadEmailOutbox() throws Exception {
        mockMvc.perform(get("/api/admin/email-outbox"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanFilterMessagesWithoutReceivingPrivateBody() throws Exception {
        EmailOutbox email = new EmailOutbox();
        email.setRecipient("private@example.com");
        email.setSubject("Reset your password");
        email.setBody("https://store.example/reset?token=secret");
        email.setStatus(EmailOutboxStatus.FAILED);
        email.setAttemptCount(1);
        email.setCreatedAt(LocalDateTime.now());
        email.setNextAttemptAt(LocalDateTime.now());
        repository.saveAndFlush(email);

        mockMvc.perform(get("/api/admin/email-outbox")
                        .param("status", "FAILED")
                        .param("search", "private@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recipient").value("private@example.com"))
                .andExpect(jsonPath("$.content[0].canRetry").value(true))
                .andExpect(jsonPath("$.content[0].body").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void supportCanReadSummary() throws Exception {
        mockMvc.perform(get("/api/admin/email-outbox/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.failed").isNumber());
    }
}
