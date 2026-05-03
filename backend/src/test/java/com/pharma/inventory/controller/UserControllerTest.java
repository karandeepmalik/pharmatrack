package com.pharma.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
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

import java.util.List;
import java.util.Map;

import com.pharma.inventory.exception.ResourceNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, AppConfig.class})
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).username("admin").fullName("Admin User")
                .email("admin@pharma.com").role(User.Role.ADMIN).active(true).password("hashed").build();
        regularUser = User.builder().id(2L).username("john.doe").fullName("John Doe")
                .email("john@pharma.com").role(User.Role.USER).active(true).password("hashed").build();
    }

    // ── GET /api/users ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/users — list all users")
    class GetAllUsers {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanListUsers() throws Exception {
            when(userService.getAll()).thenReturn(List.of(adminUser, regularUser));
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].username").value("admin"))
                    .andExpect(jsonPath("$[1].username").value("john.doe"));
        }

        @Test
        @WithMockUser(roles = "USER")
        void regularUserCannotListUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedCannotListUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/users ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/users — admin creates a new user")
    class CreateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanCreateUser() throws Exception {
            when(userService.register(any())).thenReturn(regularUser);
            var body = Map.of(
                    "username", "john.doe",
                    "email", "john@pharma.com",
                    "fullName", "John Doe",
                    "password", "Password1!",
                    "role", "USER"
            );
            mockMvc.perform(post("/api/users").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("john.doe"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanCreateAdminUser() throws Exception {
            var newAdmin = User.builder().id(3L).username("admin2").fullName("Admin Two")
                    .email("admin2@pharma.com").role(User.Role.ADMIN).active(true).password("hashed").build();
            when(userService.register(any())).thenReturn(newAdmin);
            var body = Map.of(
                    "username", "admin2",
                    "email", "admin2@pharma.com",
                    "fullName", "Admin Two",
                    "password", "Password1!",
                    "role", "ADMIN"
            );
            mockMvc.perform(post("/api/users").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void createUserWithMissingFieldsReturns400() throws Exception {
            var body = Map.of("username", "x"); // missing required fields
            mockMvc.perform(post("/api/users").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "USER")
        void regularUserCannotCreateUser() throws Exception {
            var body = Map.of(
                    "username", "new", "email", "new@pharma.com",
                    "fullName", "New", "password", "Password1!"
            );
            mockMvc.perform(post("/api/users").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedCannotCreateUser() throws Exception {
            var body = Map.of(
                    "username", "new", "email", "new@pharma.com",
                    "fullName", "New", "password", "Password1!"
            );
            mockMvc.perform(post("/api/users").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/users/{id}/toggle ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/users/{id}/toggle — admin toggles user active status")
    class ToggleUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanToggleUser() throws Exception {
            var deactivated = User.builder().id(2L).username("john.doe").fullName("John Doe")
                    .email("john@pharma.com").role(User.Role.USER).active(false).password("hashed").build();
            when(userService.toggleActive(2L)).thenReturn(deactivated);
            mockMvc.perform(post("/api/users/2/toggle").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
        }

        @Test
        @WithMockUser(roles = "USER")
        void regularUserCannotToggle() throws Exception {
            mockMvc.perform(post("/api/users/2/toggle").with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ── GET /api/users/me ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/users/me — current user profile")
    class GetMe {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void returnsCurrentUser() throws Exception {
            when(userService.getByUsername("admin")).thenReturn(adminUser);
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("admin"))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        void unauthenticatedCannotGetMe() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── DELETE /api/users/{id} — admin deletes user ────────────────────

    @Nested
    @DisplayName("DELETE /api/users/{id} — admin deletes a user")
    class DeleteUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanDeleteUser() throws Exception {
            doNothing().when(userService).deleteUser(2L);
            mockMvc.perform(delete("/api/users/2").with(csrf()))
                    .andExpect(status().isNoContent());
            verify(userService).deleteUser(2L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void deleteNonExistentUserReturns404() throws Exception {
            doThrow(new ResourceNotFoundException("User", 999L)).when(userService).deleteUser(999L);
            mockMvc.perform(delete("/api/users/999").with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "USER")
        void regularUserCannotDelete() throws Exception {
            mockMvc.perform(delete("/api/users/2").with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedCannotDelete() throws Exception {
            mockMvc.perform(delete("/api/users/2").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── PUT /api/users/{id}/password — admin changes user password ─────

    @Nested
    @DisplayName("PUT /api/users/{id}/password — admin changes any user's password")
    class AdminChangePassword {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanChangeUserPassword() throws Exception {
            doNothing().when(userService).adminChangePassword(eq(2L), eq("NewPass@123"));
            mockMvc.perform(put("/api/users/2/password").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("newPassword", "NewPass@123"))))
                    .andExpect(status().isOk());
            verify(userService).adminChangePassword(2L, "NewPass@123");
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void serviceThrownExceptionPropagates() throws Exception {
            doThrow(new IllegalArgumentException("Password must be at least 8 characters"))
                    .when(userService).adminChangePassword(eq(2L), any());
            mockMvc.perform(put("/api/users/2/password").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("newPassword", "short"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "USER")
        void regularUserCannotChangeOtherUserPassword() throws Exception {
            mockMvc.perform(put("/api/users/2/password").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("newPassword", "NewPass@123"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedCannotChangePassword() throws Exception {
            mockMvc.perform(put("/api/users/2/password").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("newPassword", "NewPass@123"))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
