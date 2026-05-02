package com.pharma.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.inventory.dto.InventoryRequest;
import com.pharma.inventory.dto.InventoryResponse;
import com.pharma.inventory.dto.SystemInventoryRequest;
import com.pharma.inventory.exception.InsufficientInventoryException;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.service.InventoryService;
import com.pharma.inventory.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean InventoryService inventoryService;
    @MockBean UserService userService;

    private InventoryResponse sampleResponse;
    private User mockUser;

    @BeforeEach
    void setUp() {
        sampleResponse = new InventoryResponse();
        sampleResponse.setId(1L);
        sampleResponse.setUserId(2L);
        sampleResponse.setUsername("john.doe");
        sampleResponse.setMedicineId(1L);
        sampleResponse.setMedicineName("FIP Vial 10mg/ml");
        sampleResponse.setMedicineType("VIAL");
        sampleResponse.setSpecification(10.0);
        sampleResponse.setSpecUnit("mg/ml");
        sampleResponse.setPharmaId(1L);
        sampleResponse.setPharmaName("FIP Shield");
        sampleResponse.setQuantity(100);

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
                    .andExpect(jsonPath("$[0].specUnit").value("mg/ml"));
        }

        @Test @WithMockUser(roles = "USER")
        void userForbidden() throws Exception {
            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedForbidden() throws Exception {
            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /api/inventory/system ─────────────────────────────────────

    @Nested @DisplayName("GET /api/inventory/system — system inventory")
    class GetSystem {
        @Test @WithMockUser(roles = "ADMIN")
        void adminCanGetSystem() throws Exception {
            InventoryResponse sysResp = new InventoryResponse();
            sysResp.setUsername("lostinventory");
            sysResp.setQuantity(400);
            when(inventoryService.getSystemInventory()).thenReturn(List.of(sysResp));
            mockMvc.perform(get("/api/inventory/system"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].username").value("lostinventory"))
                    .andExpect(jsonPath("$[0].quantity").value(400));
        }

        @Test @WithMockUser(roles = "USER")
        void userForbiddenSystem() throws Exception {
            mockMvc.perform(get("/api/inventory/system"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── POST /api/inventory/system ────────────────────────────────────

    @Nested @DisplayName("POST /api/inventory/system — add system inventory")
    class AddSystem {
        @Test @WithMockUser(roles = "ADMIN")
        void adminCanAddSystemInventory() throws Exception {
            SystemInventoryRequest req = new SystemInventoryRequest();
            req.setMedicineId(1L);
            req.setQuantity(100);
            when(inventoryService.addSystemInventory(any())).thenReturn(sampleResponse);
            mockMvc.perform(post("/api/inventory/system").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsZeroQuantity() throws Exception {
            SystemInventoryRequest req = new SystemInventoryRequest();
            req.setMedicineId(1L);
            req.setQuantity(0);
            mockMvc.perform(post("/api/inventory/system").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingMedicineId() throws Exception {
            SystemInventoryRequest req = new SystemInventoryRequest();
            req.setQuantity(10);
            mockMvc.perform(post("/api/inventory/system").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userCannotAddSystemInventory() throws Exception {
            SystemInventoryRequest req = new SystemInventoryRequest();
            req.setMedicineId(1L);
            req.setQuantity(100);
            mockMvc.perform(post("/api/inventory/system").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    // ── POST /api/inventory/allocate ──────────────────────────────────

    @Nested @DisplayName("POST /api/inventory/allocate — allocate to user")
    class Allocate {
        @Test @WithMockUser(roles = "ADMIN")
        void adminCanAllocate() throws Exception {
            InventoryRequest req = new InventoryRequest();
            req.setUserId(2L);
            req.setMedicineId(1L);
            req.setQuantity(50);
            when(inventoryService.allocateToUser(any())).thenReturn(sampleResponse);
            mockMvc.perform(post("/api/inventory/allocate").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("john.doe"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsConflictWhenInsufficientSystemInventory() throws Exception {
            InventoryRequest req = new InventoryRequest();
            req.setUserId(2L);
            req.setMedicineId(1L);
            req.setQuantity(9999);
            when(inventoryService.allocateToUser(any()))
                    .thenThrow(new InsufficientInventoryException(100, 9999));
            mockMvc.perform(post("/api/inventory/allocate").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingUserId() throws Exception {
            InventoryRequest req = new InventoryRequest();
            req.setMedicineId(1L);
            req.setQuantity(50);
            mockMvc.perform(post("/api/inventory/allocate").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userCannotAllocate() throws Exception {
            InventoryRequest req = new InventoryRequest();
            req.setUserId(2L);
            req.setMedicineId(1L);
            req.setQuantity(10);
            mockMvc.perform(post("/api/inventory/allocate").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
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
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCannotUseAvailableEndpoint() throws Exception {
            mockMvc.perform(get("/api/inventory/available"))
                    .andExpect(status().isForbidden());
        }
    }
}
