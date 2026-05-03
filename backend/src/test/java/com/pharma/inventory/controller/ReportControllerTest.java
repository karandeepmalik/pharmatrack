package com.pharma.inventory.controller;

import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, AppConfig.class})
class ReportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ReportService reportService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    private ReportResponse sampleReport(String type) {
        return new ReportResponse(type, "01 Jan 2025, 12:00 PM", "Sample report content for " + type);
    }

    @Nested @DisplayName("GET /api/reports/inventory-by-user")
    class InventoryByUser {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetInventoryByUser() throws Exception {
            when(reportService.inventoryByUser()).thenReturn(sampleReport("INVENTORY_BY_USER"));
            mockMvc.perform(get("/api/reports/inventory-by-user"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("INVENTORY_BY_USER"))
                    .andExpect(jsonPath("$.content").value("Sample report content for INVENTORY_BY_USER"));
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(get("/api/reports/inventory-by-user"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/reports/inventory-by-user"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested @DisplayName("GET /api/reports/inventory-valuation")
    class InventoryValuation {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetValuation() throws Exception {
            when(reportService.inventoryValuation()).thenReturn(sampleReport("INVENTORY_VALUATION"));
            mockMvc.perform(get("/api/reports/inventory-valuation"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("INVENTORY_VALUATION"));
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(get("/api/reports/inventory-valuation"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/reports/inventory-valuation"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested @DisplayName("GET /api/reports/today-sales")
    class TodaySales {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetTodaySales() throws Exception {
            when(reportService.todaySales()).thenReturn(sampleReport("TODAY_SALES"));
            mockMvc.perform(get("/api/reports/today-sales"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("TODAY_SALES"));
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(get("/api/reports/today-sales"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/reports/today-sales"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
