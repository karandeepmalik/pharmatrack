package com.pharma.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.dto.AdjustInventoryRequest;
import com.pharma.inventory.dto.InventoryAdjustmentResponse;
import com.pharma.inventory.dto.InventoryResponse;
import com.pharma.inventory.exception.InsufficientInventoryException;
import com.pharma.inventory.exception.ResourceNotFoundException;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.service.MedicineStockService;
import com.pharma.inventory.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, AppConfig.class})
class InventoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MedicineStockService medicineStockService;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    private InventoryResponse sampleResponse;
    private User mockUser;

    @BeforeEach
    void setUp() {
        sampleResponse = new InventoryResponse();
        sampleResponse.setId(1L);
        sampleResponse.setUserId(2L);
        sampleResponse.setUsername("john.doe");
        sampleResponse.setMedicineId(1L);
        sampleResponse.setMedicineName("Shield FX Vial 10 ml");
        sampleResponse.setMedicineType("VIAL");
        sampleResponse.setSpecification(10.0);
        sampleResponse.setSpecUnit("mg/ml");
        sampleResponse.setPharmaId(1L);
        sampleResponse.setPharmaName("Shield FX");
        sampleResponse.setQuantity(BigDecimal.valueOf(100));
        sampleResponse.setPrice(4000);

        mockUser = User.builder().id(2L).username("john.doe").role(User.Role.USER).active(true)
                .email("j@j.com").fullName("John Doe").password("h").build();
    }

    // ── GET /api/inventory ────────────────────────────────────────────

    @Nested @DisplayName("GET /api/inventory — all user inventories")
    class GetAll {
        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetAll() throws Exception {
            when(medicineStockService.getAll()).thenReturn(List.of(sampleResponse));
            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].username").value("john.doe"))
                    .andExpect(jsonPath("$[0].specUnit").value("mg/ml"))
                    .andExpect(jsonPath("$[0].price").value(4000));
        }

        @Test @WithMockUser(roles = "USER")
        void userForbidden() throws Exception {
            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedUnauthorized() throws Exception {
            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /api/inventory/available ──────────────────────────────────

    @Nested @DisplayName("GET /api/inventory/available — user's inventory")
    class GetAvailable {
        @Test @WithMockUser(roles = "USER", username = "john.doe")
        void userCanGetAvailable() throws Exception {
            when(userService.getByUsername("john.doe")).thenReturn(mockUser);
            when(medicineStockService.getAvailableForUser(2L)).thenReturn(List.of(sampleResponse));
            mockMvc.perform(get("/api/inventory/available"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].price").value(4000));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminForbidden() throws Exception {
            mockMvc.perform(get("/api/inventory/available"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedUnauthorized() throws Exception {
            mockMvc.perform(get("/api/inventory/available"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/inventory/adjust ────────────────────────────────────

    @Nested @DisplayName("POST /api/inventory/adjust — admin adjusts user inventory")
    class Adjust {

        private AdjustInventoryRequest validAddReq() {
            AdjustInventoryRequest r = new AdjustInventoryRequest();
            r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(BigDecimal.TEN);
            r.setAdjustmentType("ADD"); r.setNote("Restocking for Ward 3");
            return r;
        }

        private AdjustInventoryRequest validReduceReq() {
            AdjustInventoryRequest r = new AdjustInventoryRequest();
            r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(BigDecimal.valueOf(5));
            r.setAdjustmentType("REDUCE"); r.setNote("Returned expired stock");
            return r;
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanAddInventory() throws Exception {
            InventoryResponse added = new InventoryResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustInventory(any(), any())).thenReturn(added);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAddReq())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(110));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanReduceInventory() throws Exception {
            InventoryResponse reduced = new InventoryResponse();
            reduced.setQuantity(BigDecimal.valueOf(45)); reduced.setUsername("john.doe");
            when(medicineStockService.adjustInventory(any(), any())).thenReturn(reduced);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validReduceReq())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(45));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsConflictWhenInsufficientStock() throws Exception {
            when(medicineStockService.adjustInventory(any(), any()))
                    .thenThrow(new InsufficientInventoryException(BigDecimal.valueOf(3), BigDecimal.valueOf(5)));
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validReduceReq())))
                    .andExpect(status().isConflict());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsZeroQuantity() throws Exception {
            AdjustInventoryRequest req = validAddReq();
            req.setQuantity(BigDecimal.ZERO);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingNote() throws Exception {
            AdjustInventoryRequest req = validAddReq();
            req.setNote(null);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsNoteTooShort() throws Exception {
            AdjustInventoryRequest req = validAddReq();
            req.setNote("ok");
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsInvalidAdjustmentType() throws Exception {
            AdjustInventoryRequest req = validAddReq();
            req.setAdjustmentType("INVALID");
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingUserId() throws Exception {
            AdjustInventoryRequest req = validAddReq();
            req.setUserId(null);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userCannotAdjust() throws Exception {
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAddReq())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedCannotAdjust() throws Exception {
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAddReq())))
                    .andExpect(status().isUnauthorized());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void acceptsInTransitFlagTrue() throws Exception {
            InventoryResponse added = new InventoryResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustInventory(any(), any())).thenReturn(added);
            AdjustInventoryRequest req = validAddReq();
            req.setInTransit(true);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(110));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void acceptsInTransitFlagFalse() throws Exception {
            InventoryResponse added = new InventoryResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustInventory(any(), any())).thenReturn(added);
            AdjustInventoryRequest req = validAddReq();
            req.setInTransit(false);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void acceptsValidAdjustmentDate() throws Exception {
            InventoryResponse added = new InventoryResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustInventory(any(), any())).thenReturn(added);
            AdjustInventoryRequest req = validAddReq();
            req.setAdjustmentDate(LocalDate.now().minusDays(1));
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(110));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsFutureAdjustmentDate() throws Exception {
            AdjustInventoryRequest req = validAddReq();
            req.setAdjustmentDate(LocalDate.now().plusDays(1));
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/inventory/adjustments ────────────────────────────────

    @Nested @DisplayName("GET /api/inventory/adjustments")
    class GetAdjustments {

        private InventoryAdjustmentResponse sampleAdj() {
            return InventoryAdjustmentResponse.builder()
                    .id(1L).userId(2L).username("john.doe").userFullName("John Doe")
                    .medicineId(1L).medicineName("Shield FX Vial 10 ml")
                    .medicineType("VIAL").specification(10.0)
                    .quantity(BigDecimal.TEN).adjustmentType("ADD").note("Restocking Ward 3")
                    .adjustedAt("01 Jun 2026, 10:00 AM").inTransit(false).transitDays(2)
                    .internalMovement(false).inventoryType("REGULAR_MEDICINE_STOCK")
                    .build();
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetAdjustments() throws Exception {
            when(medicineStockService.getAdjustments(any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(sampleAdj()));
            mockMvc.perform(get("/api/inventory/adjustments")
                            .param("from", "2026-06-01")
                            .param("to", "2026-06-06"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].username").value("john.doe"))
                    .andExpect(jsonPath("$[0].adjustmentType").value("ADD"))
                    .andExpect(jsonPath("$[0].quantity").value(10));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsEmptyListWhenNoAdjustments() throws Exception {
            when(medicineStockService.getAdjustments(any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());
            mockMvc.perform(get("/api/inventory/adjustments")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void missingFromParamReturnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/inventory/adjustments").param("to", "2026-06-06"))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void missingToParamReturnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/inventory/adjustments").param("from", "2026-06-01"))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(get("/api/inventory/adjustments")
                            .param("from", "2026-06-01").param("to", "2026-06-06"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/inventory/adjustments")
                            .param("from", "2026-06-01").param("to", "2026-06-06"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── DELETE /api/inventory/adjustments/{id} ────────────────────────

    @Nested @DisplayName("DELETE /api/inventory/adjustments/{id}")
    class DeleteAdjustment {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanDeleteAdjustment() throws Exception {
            doNothing().when(medicineStockService).deleteAdjustment(1L);
            mockMvc.perform(delete("/api/inventory/adjustments/1").with(csrf()))
                    .andExpect(status().isNoContent());
            verify(medicineStockService).deleteAdjustment(1L);
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsNotFoundForMissingAdjustment() throws Exception {
            doThrow(new ResourceNotFoundException("InventoryAdjustment", 999L))
                    .when(medicineStockService).deleteAdjustment(999L);
            mockMvc.perform(delete("/api/inventory/adjustments/999").with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(delete("/api/inventory/adjustments/1").with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/inventory/adjustments/1").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
