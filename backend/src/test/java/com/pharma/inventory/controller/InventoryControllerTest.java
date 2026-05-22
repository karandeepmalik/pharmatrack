package com.pharma.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.dto.AdjustInventoryRequest;
import com.pharma.inventory.dto.InventoryResponse;
import com.pharma.inventory.exception.InsufficientInventoryException;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.service.InventoryService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, AppConfig.class})
class InventoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean InventoryService inventoryService;
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
        sampleResponse.setQuantity(100);
        sampleResponse.setPrice(4000);

        mockUser = User.builder().id(2L).username("john.doe").role(User.Role.USER).active(true)
                .email("j@j.com").fullName("John Doe").password("h").build();
    }

    // ── GET /api/inventory ────────────────────────────────────────────

    @Nested @DisplayName("GET /api/inventory — all user inventories")
    class GetAll {
        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetAll() throws Exception {
            when(inventoryService.getAll()).thenReturn(List.of(sampleResponse));
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
            when(inventoryService.getAvailableForUser(2L)).thenReturn(List.of(sampleResponse));
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
            r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(10);
            r.setAdjustmentType("ADD"); r.setNote("Restocking for Ward 3");
            return r;
        }

        private AdjustInventoryRequest validReduceReq() {
            AdjustInventoryRequest r = new AdjustInventoryRequest();
            r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(5);
            r.setAdjustmentType("REDUCE"); r.setNote("Returned expired stock");
            return r;
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanAddInventory() throws Exception {
            InventoryResponse added = new InventoryResponse();
            added.setQuantity(110); added.setUsername("john.doe");
            when(inventoryService.adjustInventory(any(), any())).thenReturn(added);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAddReq())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(110));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanReduceInventory() throws Exception {
            InventoryResponse reduced = new InventoryResponse();
            reduced.setQuantity(45); reduced.setUsername("john.doe");
            when(inventoryService.adjustInventory(any(), any())).thenReturn(reduced);
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validReduceReq())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(45));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsConflictWhenInsufficientStock() throws Exception {
            when(inventoryService.adjustInventory(any(), any()))
                    .thenThrow(new InsufficientInventoryException(3, 5));
            mockMvc.perform(post("/api/inventory/adjust").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validReduceReq())))
                    .andExpect(status().isConflict());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsZeroQuantity() throws Exception {
            AdjustInventoryRequest req = validAddReq();
            req.setQuantity(0);
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
        void acceptsValidAdjustmentDate() throws Exception {
            InventoryResponse added = new InventoryResponse();
            added.setQuantity(110); added.setUsername("john.doe");
            when(inventoryService.adjustInventory(any(), any())).thenReturn(added);
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
}
