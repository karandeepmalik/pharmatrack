package com.pharma.inventory.controller;

import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.dto.TelemetryEvent;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.service.TelemetryService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
@Import({SecurityConfig.class, AppConfig.class})
@DisplayName("TelemetryController")
class TelemetryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  TelemetryService telemetryService;
    @MockBean  JwtService jwtService;
    @MockBean  UserRepository userRepository;

    private static final String VALID_BODY =
            "{\"eventName\":\"report_generated\",\"page\":\"/admin/reports\",\"properties\":{\"period\":\"daily\"}}";

    @Nested @DisplayName("POST /api/telemetry")
    class Post {

        @Test @WithMockUser(roles = "ADMIN")
        void adminCanPostTelemetryEvent() throws Exception {
            mockMvc.perform(post("/api/telemetry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk());
        }

        @Test @WithMockUser(roles = "USER")
        void regularUserCanPostTelemetryEvent() throws Exception {
            mockMvc.perform(post("/api/telemetry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventName\":\"page_view\",\"page\":\"/dashboard\",\"properties\":{}}"))
                    .andExpect(status().isOk());
        }

        @Test
        void unauthenticatedRequestIsRejected() throws Exception {
            mockMvc.perform(post("/api/telemetry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test @WithMockUser(username = "john.doe", roles = "USER")
        void telemetryServiceIsCalledWithPrincipalUsername() throws Exception {
            mockMvc.perform(post("/api/telemetry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk());

            verify(telemetryService).record(eq("john.doe"), any(TelemetryEvent.class));
        }

        @Test @WithMockUser(roles = "ADMIN")
        void emptyPropertiesAreAccepted() throws Exception {
            mockMvc.perform(post("/api/telemetry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventName\":\"ping\",\"page\":\"/\",\"properties\":{}}"))
                    .andExpect(status().isOk());
        }
    }
}
