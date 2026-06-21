package com.pharma.inventory.exception;

import com.pharma.inventory.controller.TransactionController;
import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.service.ScreenshotProcessor;
import com.pharma.inventory.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.mock.web.MockMultipartFile;

import org.springframework.data.domain.Page;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, AppConfig.class})
@DisplayName("GlobalExceptionHandler HTTP status mapping")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  TransactionService transactionService;
    @MockBean  ScreenshotProcessor screenshotProcessor;
    @MockBean  JwtService jwtService;
    @MockBean  UserRepository userRepository;

    @Nested @DisplayName("404 Not Found")
    class NotFound {
        @Test @WithMockUser(roles = "ADMIN")
        @DisplayName("ResourceNotFoundException → 404 with envelope")
        void resourceNotFound_returns404() throws Exception {
            when(transactionService.getAllPaged(anyString(), anyInt(), anyInt()))
                    .thenThrow(new ResourceNotFoundException("Transaction", 99L));

            mockMvc.perform(get("/api/transactions"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Transaction not found: 99"));
        }
    }

    @Nested @DisplayName("409 Conflict")
    class Conflict {
        @Test @WithMockUser(roles = "ADMIN")
        @DisplayName("InsufficientInventoryException → 409")
        void insufficientInventory_returns409() throws Exception {
            when(transactionService.approve(any(), any(), anyString()))
                    .thenThrow(new InsufficientInventoryException(5, 10));

            mockMvc.perform(post("/api/transactions/1/approve").with(csrf())
                    .contentType("application/json").content("{\"approved\":true}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("5")));
        }

        @Test @WithMockUser(roles = "ADMIN")
        @DisplayName("InvalidStateTransitionException → 409")
        void invalidStateTransition_returns409() throws Exception {
            when(transactionService.approve(any(), any(), anyString()))
                    .thenThrow(new InvalidStateTransitionException("APPROVED", "approve"));

            mockMvc.perform(post("/api/transactions/1/approve").with(csrf())
                    .contentType("application/json").content("{\"approved\":true}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("APPROVED")));
        }
    }

    @Nested @DisplayName("400 Bad Request")
    class BadRequest {
        @Test @WithMockUser(roles = "USER")
        @DisplayName("InvalidScreenshotException → 400")
        void invalidScreenshot_returns400() throws Exception {
            when(screenshotProcessor.encodeAll(any()))
                    .thenThrow(new InvalidScreenshotException("Must be an image file"));

            mockMvc.perform(multipart("/api/transactions")
                    .file(new MockMultipartFile("screenshots", "bad.pdf", "application/pdf", "pdf".getBytes()))
                    .param("medicineId", "1").param("quantity", "5")
                    .param("notes", "Valid clinic dispatch note").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test @WithMockUser(roles = "USER")
        @DisplayName("missing required parameter → 400")
        void missingParam_returns400() throws Exception {
            mockMvc.perform(multipart("/api/transactions")
                    .param("quantity", "5").param("notes", "Valid note five")
                    .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser(roles = "USER")
        @DisplayName("IllegalArgumentException → 400 with message")
        void illegalArgument_returns400() throws Exception {
            when(screenshotProcessor.encodeAll(any()))
                    .thenReturn(List.<String[]>of(new String[]{"anY=", "image/png"}));
            when(transactionService.submit(any(), anyString()))
                    .thenThrow(new IllegalArgumentException("Note must be between 5 and 500 characters"));

            mockMvc.perform(multipart("/api/transactions")
                    .file(new MockMultipartFile("screenshots", "pay.png", "image/png",
                            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}))
                    .param("medicineId", "1").param("quantity", "5")
                    .param("notes", "Hi").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("5 and 500")));
        }
    }

    @Nested @DisplayName("Error envelope")
    class Envelope {
        @Test @WithMockUser(roles = "ADMIN")
        @DisplayName("always includes status, error, message, timestamp, path")
        void errorEnvelope_hasAllFields() throws Exception {
            when(transactionService.getAllPaged(anyString(), anyInt(), anyInt()))
                    .thenThrow(new ResourceNotFoundException("Transaction", 1L));

            mockMvc.perform(get("/api/transactions"))
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.path").value("/api/transactions"));
        }
    }
}
