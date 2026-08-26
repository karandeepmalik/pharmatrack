package com.pharma.medicinestock.service;

import com.pharma.medicinestock.dto.RegisterRequest;
import com.pharma.medicinestock.dto.UserResponse;
import com.pharma.medicinestock.entity.User;
import com.pharma.medicinestock.exception.ResourceNotFoundException;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import com.pharma.medicinestock.repository.MedicineStockRepository;
import com.pharma.medicinestock.repository.TransactionRepository;
import com.pharma.medicinestock.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock MedicineStockRepository medicineStockRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock MedicineStockAdjustmentRepository adjustmentRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(2L).username("john.doe").fullName("John Doe")
                .email("john@pharma.com").role(User.Role.USER).active(true).password("hashed").build();
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        void deletesMedicineStockTransactionsAndUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));

            userService.deleteUser(2L);

            verify(transactionRepository).nullifyApprovedBy(2L);
            verify(transactionRepository).deleteBySubmittedById(2L);
            verify(adjustmentRepository).nullifyAdjustedBy(2L);
            verify(adjustmentRepository).deleteByUserId(2L);
            verify(medicineStockRepository).deleteByUserId(2L);
            verify(userRepository).delete(user);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(transactionRepository, medicineStockRepository);
            verify(userRepository, never()).delete(any());
        }

        @Test
        void deletionOrderIsCorrect() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            var order = inOrder(transactionRepository, adjustmentRepository, medicineStockRepository, userRepository);

            userService.deleteUser(2L);

            order.verify(transactionRepository).nullifyApprovedBy(2L);
            order.verify(transactionRepository).deleteBySubmittedById(2L);
            order.verify(adjustmentRepository).nullifyAdjustedBy(2L);
            order.verify(adjustmentRepository).deleteByUserId(2L);
            order.verify(medicineStockRepository).deleteByUserId(2L);
            order.verify(userRepository).delete(user);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {

        @Test
        void togglesUserFromActiveToInactive() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserResponse result = userService.toggleActive(2L);

            assertThat(result.isActive()).isFalse();
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.toggleActive(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateLocation")
    class UpdateLocation {

        @Test
        void setsLocationOnTheUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserResponse result = userService.updateLocation(2L, "Delhi");

            assertThat(result.getLocation()).isEqualTo("Delhi");
            assertThat(user.getLocation()).isEqualTo("Delhi");
        }

        @Test
        void blankLocationClearsItToNull() {
            user.setLocation("Delhi");
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserResponse result = userService.updateLocation(2L, "   ");

            assertThat(result.getLocation()).isNull();
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.updateLocation(99L, "Delhi"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        private RegisterRequest req;

        @BeforeEach
        void setUp() {
            req = new RegisterRequest();
            req.setUsername("new.hire"); req.setEmail("new.hire@pharma.com");
            req.setPassword("password123"); req.setFullName("New Hire");
            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void setsLocationWhenProvided() {
            req.setLocation("Bangalore");

            UserResponse result = userService.register(req);

            assertThat(result.getLocation()).isEqualTo("Bangalore");
        }

        @Test
        void locationRemainsNullWhenNotProvided() {
            UserResponse result = userService.register(req);

            assertThat(result.getLocation()).isNull();
        }
    }
}
