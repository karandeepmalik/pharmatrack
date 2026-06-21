package com.pharma.inventory.controller;

import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.dto.SalesGraphResponse;
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

import org.mockito.ArgumentMatchers;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
            when(reportService.inventoryValuation((LocalDate) null)).thenReturn(sampleReport("INVENTORY_VALUATION"));
            mockMvc.perform(get("/api/reports/inventory-valuation"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("INVENTORY_VALUATION"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetValuationWithDateParam() throws Exception {
            when(reportService.inventoryValuation(ArgumentMatchers.any(LocalDate.class)))
                    .thenReturn(sampleReport("INVENTORY_VALUATION"));
            mockMvc.perform(get("/api/reports/inventory-valuation").param("date", "2026-05-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("INVENTORY_VALUATION"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetValuationWithoutDateParam() throws Exception {
            when(reportService.inventoryValuation((LocalDate) null))
                    .thenReturn(sampleReport("INVENTORY_VALUATION"));
            mockMvc.perform(get("/api/reports/inventory-valuation"))
                    .andExpect(status().isOk());
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
        void adminCanGetTodaySalesWithNoParams() throws Exception {
            when(reportService.todaySales(ArgumentMatchers.isNull(), ArgumentMatchers.isNull()))
                    .thenReturn(sampleReport("TODAY_SALES"));
            mockMvc.perform(get("/api/reports/today-sales"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("TODAY_SALES"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetSalesForDateRange() throws Exception {
            when(reportService.todaySales(ArgumentMatchers.any(LocalDate.class), ArgumentMatchers.any(LocalDate.class)))
                    .thenReturn(sampleReport("TODAY_SALES"));
            mockMvc.perform(get("/api/reports/today-sales")
                            .param("from", "2026-05-01")
                            .param("to", "2026-05-07"))
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

    @Nested @DisplayName("GET /api/reports/daily")
    class DailyReport {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetDailyReportWithoutDate() throws Exception {
            when(reportService.dailyReport(ArgumentMatchers.isNull()))
                    .thenReturn(sampleReport("DAILY_REPORT"));
            mockMvc.perform(get("/api/reports/daily"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("DAILY_REPORT"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetDailyReportForSpecificDate() throws Exception {
            when(reportService.dailyReport(ArgumentMatchers.any()))
                    .thenReturn(sampleReport("DAILY_REPORT"));
            mockMvc.perform(get("/api/reports/daily").param("date", "2026-05-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportType").value("DAILY_REPORT"));
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(get("/api/reports/daily"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/reports/daily"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested @DisplayName("GET /api/reports/sales-graph")
    class SalesGraph {

        private SalesGraphResponse sampleSalesGraph() {
            List<SalesGraphResponse.SpecBreakdown> specs = List.of(
                    new SalesGraphResponse.SpecBreakdown("Vial 10 ml", 5, 20000L),
                    new SalesGraphResponse.SpecBreakdown("Vial 5 ml",  3, 9000L)
            );
            return new SalesGraphResponse("daily", List.of(
                    new SalesGraphResponse.DataPoint("15 Jun", 8, 29000L, specs)
            ));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetSalesGraphWithDefaults() throws Exception {
            when(reportService.salesGraph(any(), any(), any())).thenReturn(sampleSalesGraph());
            mockMvc.perform(get("/api/reports/sales-graph"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("daily"))
                    .andExpect(jsonPath("$.dataPoints").isArray());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void responseIncludesSpecBreakdownArray() throws Exception {
            when(reportService.salesGraph(any(), any(), any())).thenReturn(sampleSalesGraph());
            mockMvc.perform(get("/api/reports/sales-graph")
                            .param("period", "daily")
                            .param("from", "2026-06-01")
                            .param("to",   "2026-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataPoints[0].specs").isArray())
                    .andExpect(jsonPath("$.dataPoints[0].specs[0].specName").value("Vial 10 ml"))
                    .andExpect(jsonPath("$.dataPoints[0].specs[0].quantity").value(5))
                    .andExpect(jsonPath("$.dataPoints[0].specs[1].specName").value("Vial 5 ml"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void totalQuantityAndValueSerialised() throws Exception {
            when(reportService.salesGraph(any(), any(), any())).thenReturn(sampleSalesGraph());
            mockMvc.perform(get("/api/reports/sales-graph"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataPoints[0].quantity").value(8))
                    .andExpect(jsonPath("$.dataPoints[0].value").value(29000));
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(get("/api/reports/sales-graph"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/reports/sales-graph"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
