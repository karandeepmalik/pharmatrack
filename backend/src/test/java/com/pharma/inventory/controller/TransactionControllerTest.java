package com.pharma.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.exception.InvalidScreenshotException;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.service.ScreenshotProcessor;
import com.pharma.inventory.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, AppConfig.class})
class TransactionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TransactionService transactionService;
    @MockBean ScreenshotProcessor screenshotProcessor;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    private TransactionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new TransactionResponse();
        sampleResponse.setId(1L);
        sampleResponse.setStatus("PENDING");
        sampleResponse.setQuantity(10);
        sampleResponse.setMedicineName("FIP Shield Vial");
        sampleResponse.setMedicineType("VIAL");
        sampleResponse.setSpecification(10.0);
        sampleResponse.setPharmaName("FIP Shield");
        sampleResponse.setNotes("Dispatched to clinic B for FIP treatment");
        sampleResponse.setSubmittedByUsername("john.doe");
        sampleResponse.setSubmittedAt(LocalDateTime.now());
    }

    // ── POST /api/transactions (multipart) ─────────────────────────────

    @Nested
    @DisplayName("POST /api/transactions — multipart form data")
    class MultipartSubmit {

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("submit without screenshot returns 400 — screenshot is mandatory")
        void submit_noScreenshot_400() throws Exception {
            mockMvc.perform(multipart("/api/transactions")
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .param("notes", "Dispatched to clinic B for FIP treatment")
                    .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("submit with PNG screenshot encodes it to Base64 and sets MIME type")
        void submit_withPngScreenshot_encodedAndStored() throws Exception {
            byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG magic bytes
            MockMultipartFile screenshot = new MockMultipartFile(
                    "screenshot", "payment.png", "image/png", pngBytes);

            String expectedB64 = Base64.getEncoder().encodeToString(pngBytes);

            sampleResponse.setPaymentScreenshot(expectedB64);
            sampleResponse.setPaymentScreenshotType("image/png");

            when(transactionService.submit(any(), eq("john.doe"))).thenReturn(sampleResponse);

            mockMvc.perform(multipart("/api/transactions")
                    .file(screenshot)
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .param("notes", "Dispatched to clinic B for FIP treatment")
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentScreenshot").value(expectedB64))
                    .andExpect(jsonPath("$.paymentScreenshotType").value("image/png"));
        }

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("submit with JPEG screenshot is accepted")
        void submit_withJpegScreenshot_200() throws Exception {
            MockMultipartFile screenshot = new MockMultipartFile(
                    "screenshot", "payment.jpg", "image/jpeg", "jpeg-data".getBytes());

            sampleResponse.setPaymentScreenshotType("image/jpeg");
            when(transactionService.submit(any(), eq("john.doe"))).thenReturn(sampleResponse);

            mockMvc.perform(multipart("/api/transactions")
                    .file(screenshot)
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .param("notes", "Payment confirmed for clinic B dispatch")
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentScreenshotType").value("image/jpeg"));
        }

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("submit with non-image file returns 400")
        void submit_nonImageFile_400() throws Exception {
            MockMultipartFile badFile = new MockMultipartFile(
                    "screenshot", "payment.pdf", "application/pdf", "pdf-content".getBytes());

            when(screenshotProcessor.hasScreenshot(any())).thenReturn(true);
            when(screenshotProcessor.encodeToBase64(any()))
                    .thenThrow(new InvalidScreenshotException("Must be an image file"));

            mockMvc.perform(multipart("/api/transactions")
                    .file(badFile)
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .param("notes", "Clinic B dispatch confirmed payment")
                    .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("submit with missing notes returns 400")
        void submit_missingNotes_400() throws Exception {
            mockMvc.perform(multipart("/api/transactions")
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("submit with notes too short returns 400")
        void submit_notesTooShort_400() throws Exception {
            MockMultipartFile screenshot = new MockMultipartFile(
                    "screenshot", "pay.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            when(transactionService.submit(any(), anyString()))
                    .thenThrow(new IllegalArgumentException("Note must be between 5 and 500 characters"));

            mockMvc.perform(multipart("/api/transactions")
                    .file(screenshot)
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .param("notes", "Hi") // < 5 chars
                    .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("ADMIN role cannot access user submit endpoint")
        void submit_adminRole_403() throws Exception {
            mockMvc.perform(multipart("/api/transactions")
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .param("notes", "Admin trying to submit a transaction")
                    .with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void submit_noAuth_401() throws Exception {
            mockMvc.perform(multipart("/api/transactions")
                    .param("medicineId", "1")
                    .param("quantity", "10")
                    .param("notes", "Unauthorized dispatch attempt test")
                    .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /api/transactions (admin) ──────────────────────────────────

    @Nested
    @DisplayName("GET /api/transactions — admin list with screenshots")
    class AdminGetAll {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("returns list including paymentScreenshot and paymentScreenshotType")
        void getAll_includesScreenshotFields() throws Exception {
            String b64 = Base64.getEncoder().encodeToString("img-bytes".getBytes());
            sampleResponse.setPaymentScreenshot(b64);
            sampleResponse.setPaymentScreenshotType("image/png");

            when(transactionService.getAll()).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].paymentScreenshot").value(b64))
                    .andExpect(jsonPath("$[0].paymentScreenshotType").value("image/png"));
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("returns null paymentScreenshot when none was uploaded")
        void getAll_noScreenshot_nullField() throws Exception {
            sampleResponse.setPaymentScreenshot(null);
            sampleResponse.setPaymentScreenshotType(null);

            when(transactionService.getAll()).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].paymentScreenshot").doesNotExist());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("USER role cannot access admin getAll endpoint")
        void getAll_userRole_403() throws Exception {
            mockMvc.perform(get("/api/transactions"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── GET /api/transactions/my (user) ───────────────────────────────

    @Nested
    @DisplayName("GET /api/transactions/my — user history with screenshots")
    class UserGetMy {

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("returns user's transactions including screenshot data")
        void getMy_includesScreenshotFields() throws Exception {
            String b64 = Base64.getEncoder().encodeToString("my-img".getBytes());
            sampleResponse.setPaymentScreenshot(b64);
            sampleResponse.setPaymentScreenshotType("image/jpeg");

            when(transactionService.getByUser("john.doe")).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/transactions/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].paymentScreenshot").value(b64))
                    .andExpect(jsonPath("$[0].paymentScreenshotType").value("image/jpeg"));
        }
    }

    // ── POST /api/transactions/{id}/approve ────────────────────────────

    @Nested
    @DisplayName("POST /api/transactions/{id}/approve — approval preserves screenshot")
    class Approve {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("approve response includes screenshot from original submission")
        void approve_responseContainsScreenshot() throws Exception {
            String b64 = Base64.getEncoder().encodeToString("screenshot".getBytes());
            sampleResponse.setStatus("APPROVED");
            sampleResponse.setPaymentScreenshot(b64);
            sampleResponse.setPaymentScreenshotType("image/png");

            when(transactionService.approve(eq(1L), any(), eq("admin"))).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/transactions/1/approve")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"approved\": true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.paymentScreenshot").value(b64))
                    .andExpect(jsonPath("$.paymentScreenshotType").value("image/png"));
        }
    }

    // ── GET /api/transactions/history ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/transactions/history — admin date-range search")
    class History {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("returns transactions for given date range and ALL status")
        void history_allStatus_200() throws Exception {
            sampleResponse.setStatus("APPROVED");
            when(transactionService.getHistory(any(), any(), eq("ALL")))
                    .thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/transactions/history")
                    .param("from", "2026-05-01")
                    .param("to", "2026-05-04")
                    .param("status", "ALL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("APPROVED"));
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("returns only APPROVED transactions when status=APPROVED")
        void history_approvedStatus_200() throws Exception {
            sampleResponse.setStatus("APPROVED");
            when(transactionService.getHistory(any(), any(), eq("APPROVED")))
                    .thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/transactions/history")
                    .param("from", "2026-05-01")
                    .param("to", "2026-05-04")
                    .param("status", "APPROVED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("APPROVED"));
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("missing from/to returns 400")
        void history_missingParams_400() throws Exception {
            mockMvc.perform(get("/api/transactions/history")
                    .param("status", "ALL"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = "USER")
        @DisplayName("USER role cannot access history endpoint")
        void history_userRole_403() throws Exception {
            mockMvc.perform(get("/api/transactions/history")
                    .param("from", "2026-05-01")
                    .param("to", "2026-05-04"))
                    .andExpect(status().isForbidden());
        }
    }
}
