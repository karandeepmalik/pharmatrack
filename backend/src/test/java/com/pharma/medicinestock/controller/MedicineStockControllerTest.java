package com.pharma.medicinestock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.medicinestock.config.AppConfig;
import com.pharma.medicinestock.config.SecurityConfig;
import com.pharma.medicinestock.dto.AdjustMedicineStockRequest;
import com.pharma.medicinestock.dto.MedicineStockAdjustmentResponse;
import com.pharma.medicinestock.dto.MedicineStockResponse;
import com.pharma.medicinestock.dto.UserResponse;
import com.pharma.medicinestock.exception.InsufficientMedicineStockException;
import com.pharma.medicinestock.exception.ResourceNotFoundException;
import com.pharma.medicinestock.repository.UserRepository;
import com.pharma.medicinestock.security.JwtService;
import com.pharma.medicinestock.security.TokenRevocationStore;
import com.pharma.medicinestock.service.MedicineStockService;
import com.pharma.medicinestock.service.UserService;
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

@WebMvcTest(MedicineStockController.class)
@Import({SecurityConfig.class, AppConfig.class})
class MedicineStockControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MedicineStockService medicineStockService;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean TokenRevocationStore tokenRevocationStore;
    @MockBean UserRepository userRepository;

    private MedicineStockResponse sampleResponse;
    private UserResponse mockUser;

    @BeforeEach
    void setUp() {
        sampleResponse = new MedicineStockResponse();
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

        mockUser = new UserResponse();
        mockUser.setId(2L); mockUser.setUsername("john.doe"); mockUser.setRole("USER");
        mockUser.setActive(true); mockUser.setEmail("j@j.com"); mockUser.setFullName("John Doe");
    }

    // ── GET /api/medicine-stock ────────────────────────────────────────────

    @Nested @DisplayName("GET /api/medicine-stock — all user inventories")
    class GetAll {
        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetAll() throws Exception {
            when(medicineStockService.getAll()).thenReturn(List.of(sampleResponse));
            mockMvc.perform(get("/api/medicine-stock"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].username").value("john.doe"))
                    .andExpect(jsonPath("$[0].specUnit").value("mg/ml"))
                    .andExpect(jsonPath("$[0].price").value(4000));
        }

        @Test @WithMockUser(roles = "USER")
        void userForbidden() throws Exception {
            mockMvc.perform(get("/api/medicine-stock"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedUnauthorized() throws Exception {
            mockMvc.perform(get("/api/medicine-stock"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /api/medicine-stock/available ──────────────────────────────────

    @Nested @DisplayName("GET /api/medicine-stock/available — user's medicineStock")
    class GetAvailable {
        @Test @WithMockUser(roles = "USER", username = "john.doe")
        void userCanGetAvailable() throws Exception {
            when(userService.getByUsername("john.doe")).thenReturn(mockUser);
            when(medicineStockService.getAvailableForUser(2L)).thenReturn(List.of(sampleResponse));
            mockMvc.perform(get("/api/medicine-stock/available"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].price").value(4000));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminForbidden() throws Exception {
            mockMvc.perform(get("/api/medicine-stock/available"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedUnauthorized() throws Exception {
            mockMvc.perform(get("/api/medicine-stock/available"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/medicine-stock/adjust ────────────────────────────────────

    @Nested @DisplayName("POST /api/medicine-stock/adjust — admin adjusts user medicineStock")
    class Adjust {

        private AdjustMedicineStockRequest validAddReq() {
            AdjustMedicineStockRequest r = new AdjustMedicineStockRequest();
            r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(BigDecimal.TEN);
            r.setAdjustmentType("ADD"); r.setNote("Restocking for Ward 3");
            return r;
        }

        private AdjustMedicineStockRequest validReduceReq() {
            AdjustMedicineStockRequest r = new AdjustMedicineStockRequest();
            r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(BigDecimal.valueOf(5));
            r.setAdjustmentType("REDUCE"); r.setNote("Returned expired stock");
            return r;
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanAddMedicineStock() throws Exception {
            MedicineStockResponse added = new MedicineStockResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustMedicineStock(any(), any())).thenReturn(added);
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAddReq())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(110));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanReduceMedicineStock() throws Exception {
            MedicineStockResponse reduced = new MedicineStockResponse();
            reduced.setQuantity(BigDecimal.valueOf(45)); reduced.setUsername("john.doe");
            when(medicineStockService.adjustMedicineStock(any(), any())).thenReturn(reduced);
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validReduceReq())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(45));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsConflictWhenInsufficientStock() throws Exception {
            when(medicineStockService.adjustMedicineStock(any(), any()))
                    .thenThrow(new InsufficientMedicineStockException(BigDecimal.valueOf(3), BigDecimal.valueOf(5)));
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validReduceReq())))
                    .andExpect(status().isConflict());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsZeroQuantity() throws Exception {
            AdjustMedicineStockRequest req = validAddReq();
            req.setQuantity(BigDecimal.ZERO);
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingNote() throws Exception {
            AdjustMedicineStockRequest req = validAddReq();
            req.setNote(null);
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsNoteTooShort() throws Exception {
            AdjustMedicineStockRequest req = validAddReq();
            req.setNote("ok");
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsInvalidAdjustmentType() throws Exception {
            AdjustMedicineStockRequest req = validAddReq();
            req.setAdjustmentType("INVALID");
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingUserId() throws Exception {
            AdjustMedicineStockRequest req = validAddReq();
            req.setUserId(null);
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userCannotAdjust() throws Exception {
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAddReq())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedCannotAdjust() throws Exception {
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAddReq())))
                    .andExpect(status().isUnauthorized());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void acceptsInTransitFlagTrue() throws Exception {
            MedicineStockResponse added = new MedicineStockResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustMedicineStock(any(), any())).thenReturn(added);
            AdjustMedicineStockRequest req = validAddReq();
            req.setInTransit(true);
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(110));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void acceptsInTransitFlagFalse() throws Exception {
            MedicineStockResponse added = new MedicineStockResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustMedicineStock(any(), any())).thenReturn(added);
            AdjustMedicineStockRequest req = validAddReq();
            req.setInTransit(false);
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void acceptsValidAdjustmentDate() throws Exception {
            MedicineStockResponse added = new MedicineStockResponse();
            added.setQuantity(BigDecimal.valueOf(110)); added.setUsername("john.doe");
            when(medicineStockService.adjustMedicineStock(any(), any())).thenReturn(added);
            AdjustMedicineStockRequest req = validAddReq();
            req.setAdjustmentDate(LocalDate.now().minusDays(1));
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(110));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsFutureAdjustmentDate() throws Exception {
            AdjustMedicineStockRequest req = validAddReq();
            req.setAdjustmentDate(LocalDate.now().plusDays(1));
            mockMvc.perform(post("/api/medicine-stock/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/medicine-stock/adjustments ────────────────────────────────

    @Nested @DisplayName("GET /api/medicine-stock/adjustments")
    class GetAdjustments {

        private MedicineStockAdjustmentResponse sampleAdj() {
            return MedicineStockAdjustmentResponse.builder()
                    .id(1L).userId(2L).username("john.doe").userFullName("John Doe")
                    .medicineId(1L).medicineName("Shield FX Vial 10 ml")
                    .medicineType("VIAL").specification(10.0)
                    .quantity(BigDecimal.TEN).adjustmentType("ADD").note("Restocking Ward 3")
                    .adjustedAt("01 Jun 2026, 10:00 AM").inTransit(false).transitDays(2)
                    .internalMovement(false).medicineStockType("REGULAR_MEDICINE_STOCK")
                    .build();
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetAdjustments() throws Exception {
            when(medicineStockService.getAdjustments(any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(sampleAdj()));
            mockMvc.perform(get("/api/medicine-stock/adjustments")
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
            mockMvc.perform(get("/api/medicine-stock/adjustments")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void missingFromParamReturnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/medicine-stock/adjustments").param("to", "2026-06-06"))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void missingToParamReturnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/medicine-stock/adjustments").param("from", "2026-06-01"))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(get("/api/medicine-stock/adjustments")
                            .param("from", "2026-06-01").param("to", "2026-06-06"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/medicine-stock/adjustments")
                            .param("from", "2026-06-01").param("to", "2026-06-06"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── DELETE /api/medicine-stock/adjustments/{id} ────────────────────────

    @Nested @DisplayName("DELETE /api/medicine-stock/adjustments/{id}")
    class DeleteAdjustment {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanDeleteAdjustment() throws Exception {
            doNothing().when(medicineStockService).deleteAdjustment(1L);
            mockMvc.perform(delete("/api/medicine-stock/adjustments/1").with(csrf()))
                    .andExpect(status().isNoContent());
            verify(medicineStockService).deleteAdjustment(1L);
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsNotFoundForMissingAdjustment() throws Exception {
            doThrow(new ResourceNotFoundException("MedicineStockAdjustment", 999L))
                    .when(medicineStockService).deleteAdjustment(999L);
            mockMvc.perform(delete("/api/medicine-stock/adjustments/999").with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test @WithMockUser(roles = "USER")
        void userIsForbidden() throws Exception {
            mockMvc.perform(delete("/api/medicine-stock/adjustments/1").with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedIsUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/medicine-stock/adjustments/1").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
