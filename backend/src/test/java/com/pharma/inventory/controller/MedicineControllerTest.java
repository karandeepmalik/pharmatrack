package com.pharma.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.PharmaCompany;
import com.pharma.inventory.repository.MedicineRepository;
import com.pharma.inventory.repository.PharmaCompanyRepository;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MedicineController.class)
@Import({SecurityConfig.class, AppConfig.class})
class MedicineControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MedicineRepository medicineRepo;
    @MockBean PharmaCompanyRepository pharmaRepo;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    private PharmaCompany sampleCompany;
    private Medicine sampleMedicine;

    @BeforeEach
    void setUp() {
        sampleCompany = new PharmaCompany();
        sampleCompany.setId(1L);
        sampleCompany.setName("Shield FX");
        sampleCompany.setDescription("FIP treatment supplier");
        sampleCompany.setActive(true);

        sampleMedicine = new Medicine();
        sampleMedicine.setId(1L);
        sampleMedicine.setName("Shield FX Vial 10 ml");
        sampleMedicine.setType(Medicine.MedicineType.VIAL);
        sampleMedicine.setSpecification(10.0);
        sampleMedicine.setConcentrationMgPerMl(20.0);
        sampleMedicine.setPrice(4000);
        sampleMedicine.setPharmaCompany(sampleCompany);
        sampleMedicine.setActive(true);
    }

    // ── GET /api/medicines ────────────────────────────────────────────

    @Nested @DisplayName("GET /api/medicines")
    class GetAll {
        @Test @WithMockUser(roles = "ADMIN")
        void returnsAllMedicines() throws Exception {
            when(medicineRepo.findAll()).thenReturn(List.of(sampleMedicine));
            mockMvc.perform(get("/api/medicines"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Shield FX Vial 10 ml"));
        }

        @Test
        void unauthenticatedUnauthorized() throws Exception {
            mockMvc.perform(get("/api/medicines"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /api/medicines/companies ──────────────────────────────────

    @Nested @DisplayName("GET /api/medicines/companies")
    class GetCompanies {
        @Test @WithMockUser(roles = "ADMIN")
        void returnsAllCompanies() throws Exception {
            when(pharmaRepo.findAll()).thenReturn(List.of(sampleCompany));
            mockMvc.perform(get("/api/medicines/companies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Shield FX"));
        }
    }

    // ── POST /api/medicines/companies ─────────────────────────────────

    @Nested @DisplayName("POST /api/medicines/companies")
    class CreateCompany {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanCreatePharmaCompany() throws Exception {
            when(pharmaRepo.save(any(PharmaCompany.class))).thenReturn(sampleCompany);

            mockMvc.perform(post("/api/medicines/companies").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("name", "Shield FX", "description", "FIP treatment supplier"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Shield FX"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingName() throws Exception {
            mockMvc.perform(post("/api/medicines/companies").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("description", "some desc"))))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsBlankName() throws Exception {
            mockMvc.perform(post("/api/medicines/companies").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userForbidden() throws Exception {
            mockMvc.perform(post("/api/medicines/companies").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "Shield FX"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedUnauthorized() throws Exception {
            mockMvc.perform(post("/api/medicines/companies").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "Shield FX"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/medicines ───────────────────────────────────────────

    @Nested @DisplayName("POST /api/medicines")
    class CreateMedicine {

        private Map<String,Object> validMedicineRequest() {
            return Map.of(
                    "pharmaCompanyId", 1,
                    "name", "Shield FX Vial 10 ml",
                    "type", "VIAL",
                    "specification", 10.0,
                    "concentrationMgPerMl", 20.0,
                    "price", 4000
            );
        }

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanCreateMedicine() throws Exception {
            when(pharmaRepo.findById(1L)).thenReturn(Optional.of(sampleCompany));
            when(medicineRepo.save(any(Medicine.class))).thenReturn(sampleMedicine);

            mockMvc.perform(post("/api/medicines").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validMedicineRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Shield FX Vial 10 ml"));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void returnsErrorWhenPharmaCompanyNotFound() throws Exception {
            when(pharmaRepo.findById(1L)).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/medicines").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validMedicineRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingPharmaCompanyId() throws Exception {
            Map<String,Object> req = Map.of(
                    "name", "Some Med",
                    "type", "VIAL",
                    "specification", 10.0,
                    "price", 4000
            );
            mockMvc.perform(post("/api/medicines").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "ADMIN")
        void rejectsMissingRequiredFields() throws Exception {
            when(pharmaRepo.findById(1L)).thenReturn(Optional.of(sampleCompany));
            Map<String,Object> req = Map.of("pharmaCompanyId", 1, "name", "Some Med");
            mockMvc.perform(post("/api/medicines").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        void userForbidden() throws Exception {
            mockMvc.perform(post("/api/medicines").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validMedicineRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedUnauthorized() throws Exception {
            mockMvc.perform(post("/api/medicines").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validMedicineRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }
}
