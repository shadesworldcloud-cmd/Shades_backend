package com.sunglassstore.controller;

import com.sunglassstore.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityHardeningIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ProductService productService;

    @Test
    void publicResponsesCarrySecurityHeadersAndPaginationIsBounded() throws Exception {
        when(productService.getAllActiveProducts(any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/products").param("size", "10000"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Permissions-Policy"));
        verify(productService).getAllActiveProducts(argThatPageSize(200));
    }

    @Test
    void anonymousUsersCannotReadApiDocumentation() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customersCannotReadApiDocumentation() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isForbidden());
    }

    private Pageable argThatPageSize(int expected) {
        return org.mockito.ArgumentMatchers.argThat(pageable -> {
            assertEquals(expected, pageable.getPageSize());
            return true;
        });
    }
}
