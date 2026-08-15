package com.sunglassstore.controller;

import com.sunglassstore.entity.enums.ReviewStatus;
import com.sunglassstore.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ReviewService reviewService;

    @Test
    void anonymousUserCannotReadModerationQueue() throws Exception {
        mockMvc.perform(get("/api/reviews/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotModerateReviews() throws Exception {
        mockMvc.perform(patch("/api/reviews/admin/40/status").with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadModerationQueue() throws Exception {
        when(reviewService.getReviewsForModeration(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/reviews/admin").param("status", "PENDING"))
                .andExpect(status().isOk());

        verify(reviewService).getReviewsForModeration(eq(ReviewStatus.PENDING), eq(""), any());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void supportCanModerateReview() throws Exception {
        mockMvc.perform(patch("/api/reviews/admin/40/status").with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk());

        verify(reviewService).updateReviewStatus(40L, ReviewStatus.REJECTED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingModerationStatusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/reviews/admin/40/status").with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownModerationStatusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/reviews/admin/40/status").with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"REMOVED\"}"))
                .andExpect(status().isBadRequest());
    }
}
