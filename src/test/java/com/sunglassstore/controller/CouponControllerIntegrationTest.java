package com.sunglassstore.controller;

import com.sunglassstore.entity.Coupon;
import com.sunglassstore.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CouponControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private CouponService couponService;

    @Test
    void anonymousUserCannotActivateOffer() throws Exception {
        mockMvc.perform(patch("/api/coupons/3/active").with(csrf()).param("active", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotActivateOffer() throws Exception {
        mockMvc.perform(patch("/api/coupons/3/active").with(csrf()).param("active", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanActivateOffer() throws Exception {
        Coupon coupon = new Coupon(); coupon.setCouponId(3L); coupon.setCouponCode("RETURN"); coupon.setIsActive(true);
        when(couponService.setCouponActive(3L, true)).thenReturn(coupon);
        mockMvc.perform(patch("/api/coupons/3/active").with(csrf()).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
        verify(couponService).setCouponActive(3L, true);
    }
}
