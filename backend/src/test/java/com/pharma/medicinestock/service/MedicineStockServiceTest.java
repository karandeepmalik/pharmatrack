package com.pharma.medicinestock.service;

import com.pharma.medicinestock.dto.AdjustMedicineStockRequest;
import com.pharma.medicinestock.dto.MedicineStockAdjustmentResponse;
import com.pharma.medicinestock.dto.MedicineStockResponse;
import com.pharma.medicinestock.entity.*;
import com.pharma.medicinestock.exception.InsufficientMedicineStockException;
import com.pharma.medicinestock.exception.ResourceNotFoundException;
import com.pharma.medicinestock.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicineStockService")
class MedicineStockServiceTest {

    @Mock MedicineStockRepository medicineStockRepository;
    @Mock MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;
    @Mock UserRepository userRepository;
    @Mock MedicineRepository medicineRepository;
    @Mock CurrentStockCalculator currentStockCalculator;

    @InjectMocks MedicineStockService medicineStockService;

    private User user;
    private User adminUser;
    private Medicine medicine;
    private PharmaCompany pharma;
    private MedicineStock medicineStock;

    @BeforeEach
    void setUp() {
        pharma = PharmaCompany.builder().id(1L).name("Shield FX").build();

        medicine = Medicine.builder()
                .id(1L).name("Shield FX Vial 10 ml")
                .type(Medicine.MedicineType.VIAL)
                .specification(10.0).concentrationMgPerMl(20.0)
                .price(4000).pharmaCompany(pharma).active(true).build();

        user = User.builder().id(2L).username("john.doe").role(User.Role.USER)
                .active(true).email("j@j.com").fullName("John Doe").password("hashed").build();

        adminUser = User.builder().id(1L).username("admin").role(User.Role.ADMIN)
                .active(true).email("a@a.com").fullName("Admin User").password("hashed").build();

        medicineStock = MedicineStock.builder()
                .id(10L).user(user).medicine(medicine).quantity(BigDecimal.valueOf(50))
                .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK).build();
    }

    private AdjustMedicineStockRequest addReq(int qty) {
        AdjustMedicineStockRequest r = new AdjustMedicineStockRequest();
        r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(BigDecimal.valueOf(qty));
        r.setAdjustmentType("ADD"); r.setNote("Adding stock for test");
        return r;
    }

    private AdjustMedicineStockRequest reduceReq(int qty) {
        AdjustMedicineStockRequest r = new AdjustMedicineStockRequest();
        r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(BigDecimal.valueOf(qty));
        r.setAdjustmentType("REDUCE"); r.setNote("Reducing stock for test");
        return r;
    }

    // ── adjustMedicineStock ────────────────────────────────────────────────────

    @Nested
    @DisplayName("adjustMedicineStock")
    class AdjustMedicineStock {

        @Test
        @DisplayName("ADD increases quantity and returns updated response")
        void addIncreasesQuantity() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            MedicineStockResponse result = medicineStockService.adjustMedicineStock(addReq(10), "admin");

            assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(60));
        }

        @Test
        @DisplayName("REDUCE decreases quantity and returns updated response")
        void reduceDecreasesQuantity() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            MedicineStockResponse result = medicineStockService.adjustMedicineStock(reduceReq(20), "admin");

            assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(30));
        }

        @Test
        @DisplayName("creates new MedicineStock record when none exists for user+medicine+type")
        void createsNewMedicineStockWhenNotFound() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            MedicineStockResponse result = medicineStockService.adjustMedicineStock(addReq(5), "admin");

            assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            AdjustMedicineStockRequest req = addReq(5);
            req.setUserId(999L);

            assertThatThrownBy(() -> medicineStockService.adjustMedicineStock(req, "admin"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(medicineStockRepository, medicineRepository);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when target user is ADMIN")
        void throwsWhenTargetUserIsAdmin() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
            AdjustMedicineStockRequest req = addReq(5);
            req.setUserId(1L);

            assertThatThrownBy(() -> medicineStockService.adjustMedicineStock(req, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Admin user cannot hold medicine stock");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when medicine not found")
        void throwsWhenMedicineNotFound() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(999L)).thenReturn(Optional.empty());
            AdjustMedicineStockRequest req = addReq(5);
            req.setMedicineId(999L);

            assertThatThrownBy(() -> medicineStockService.adjustMedicineStock(req, "admin"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws InsufficientMedicineStockException when reducing below available quantity")
        void throwsInsufficientMedicineStock() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock)); // quantity = 50

            assertThatThrownBy(() -> medicineStockService.adjustMedicineStock(reduceReq(100), "admin"))
                    .isInstanceOf(InsufficientMedicineStockException.class);
        }

        @Test
        @DisplayName("does not throw when reducing exactly the available quantity")
        void doesNotThrowWhenReducingExactAmount() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock)); // quantity = 50
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            MedicineStockResponse result = medicineStockService.adjustMedicineStock(reduceReq(50), "admin");

            assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("saves MedicineStockAdjustment audit record with correct values")
        void savesAuditRecord() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            medicineStockService.adjustMedicineStock(addReq(7), "admin");

            verify(medicineStockAdjustmentRepository).save(argThat(adj ->
                    adj.getQuantity().compareTo(BigDecimal.valueOf(7)) == 0 &&
                    "ADD".equals(adj.getAdjustmentType()) &&
                    adj.getUser().getId().equals(2L) &&
                    adj.getMedicine().getId().equals(1L)
            ));
        }

        @Test
        @DisplayName("uses custom adjustmentDate when provided instead of now")
        void usesCustomAdjustmentDate() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustMedicineStockRequest req = addReq(5);
            req.setAdjustmentDate(LocalDate.of(2026, 1, 15));

            medicineStockService.adjustMedicineStock(req, "admin");

            verify(medicineStockAdjustmentRepository).save(argThat(adj ->
                    adj.getAdjustedAt().toLocalDate().equals(LocalDate.of(2026, 1, 15))
            ));
        }

        @Test
        @DisplayName("records inTransit=true in audit record")
        void recordsInTransitTrue() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustMedicineStockRequest req = addReq(5);
            req.setInTransit(true);

            medicineStockService.adjustMedicineStock(req, "admin");

            verify(medicineStockAdjustmentRepository).save(argThat(MedicineStockAdjustment::isInTransit));
        }

        @Test
        @DisplayName("records internalMovement=true in audit record")
        void recordsInternalMovementTrue() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustMedicineStockRequest req = addReq(5);
            req.setInternalMovement(true);

            medicineStockService.adjustMedicineStock(req, "admin");

            verify(medicineStockAdjustmentRepository).save(argThat(MedicineStockAdjustment::isInternalMovement));
        }

        @Test
        @DisplayName("stores ADMIN_MEDICINE_STOCK type when specified in request")
        void storesAdminStockType() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(
                    eq(2L), eq(1L), eq(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)))
                    .thenReturn(Optional.empty());
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustMedicineStockRequest req = addReq(5);
            req.setMedicineStockType("ADMIN_MEDICINE_STOCK");

            MedicineStockResponse result = medicineStockService.adjustMedicineStock(req, "admin");

            assertThat(result.getMedicineStockType()).isEqualTo("ADMIN_MEDICINE_STOCK");
        }

        @Test
        @DisplayName("defaults to REGULAR_MEDICINE_STOCK when invalid medicineStockType is given")
        void defaultsToRegularWhenInvalidType() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(
                    eq(2L), eq(1L), eq(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustMedicineStockRequest req = addReq(5);
            req.setMedicineStockType("NOT_A_REAL_TYPE");

            medicineStockService.adjustMedicineStock(req, "admin");

            verify(medicineStockRepository).findByUserIdAndMedicineIdAndMedicineStockType(
                    2L, 1L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
        }

        @Test
        @DisplayName("sets lastNote on medicineStock record from request note")
        void setsLastNoteOnMedicineStock() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustMedicineStockRequest req = addReq(5);
            req.setNote("Custom note for this adjustment");

            medicineStockService.adjustMedicineStock(req, "admin");

            verify(medicineStockRepository).save(argThat(inv ->
                    "Custom note for this adjustment".equals(inv.getLastNote())
            ));
        }

        @Test
        @DisplayName("maps adjustedBy to the admin performing the operation")
        void mapsAdjustedByUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));
            when(medicineStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            medicineStockService.adjustMedicineStock(addReq(3), "admin");

            verify(medicineStockAdjustmentRepository).save(argThat(adj ->
                    adj.getAdjustedBy() != null && adj.getAdjustedBy().getId().equals(1L)
            ));
        }
    }

    // ── getAvailableForUser ────────────────────────────────────────────────

    @Nested
    @DisplayName("getAvailableForUser")
    class GetAvailableForUser {

        @BeforeEach
        void noReconstructedDataByDefault() {
            // Default: CurrentStockCalculator has no data for this user, so every bucket
            // defaults to 0. Individual tests override this to exercise specific quantities.
            lenient().when(currentStockCalculator.settledQuantitiesForUser(any()))
                    .thenReturn(Map.of());
        }

        @Test
        @DisplayName("returns combined regular and admin stock")
        void returnsBothStockTypes() {
            MedicineStock regular = MedicineStock.builder().id(1L).user(user).medicine(medicine)
                    .quantity(BigDecimal.valueOf(10)).medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK).build();
            MedicineStock admin = MedicineStock.builder().id(2L).user(user).medicine(medicine)
                    .quantity(BigDecimal.valueOf(5)).medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK).build();

            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(regular));
            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(admin));

            List<MedicineStockResponse> result = medicineStockService.getAvailableForUser(2L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("regular stock appears before admin stock in result list")
        void regularBeforeAdminStock() {
            MedicineStock regular = MedicineStock.builder().id(1L).user(user).medicine(medicine)
                    .quantity(BigDecimal.valueOf(10)).medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK).build();
            MedicineStock admin = MedicineStock.builder().id(2L).user(user).medicine(medicine)
                    .quantity(BigDecimal.valueOf(5)).medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK).build();

            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(regular));
            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(admin));

            List<MedicineStockResponse> result = medicineStockService.getAvailableForUser(2L);

            assertThat(result.get(0).getMedicineStockType()).isEqualTo("REGULAR_MEDICINE_STOCK");
            assertThat(result.get(1).getMedicineStockType()).isEqualTo("ADMIN_MEDICINE_STOCK");
        }

        @Test
        @DisplayName("returns empty list when user has no medicineStock")
        void returnsEmptyWhenNoMedicineStock() {
            when(medicineStockRepository.findAvailableByUserIdAndType(any(), any())).thenReturn(List.of());

            List<MedicineStockResponse> result = medicineStockService.getAvailableForUser(2L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maps username correctly in response")
        void mapsUsernameInResponse() {
            MedicineStock inv = MedicineStock.builder().id(1L).user(user).medicine(medicine)
                    .quantity(BigDecimal.valueOf(10)).medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK).build();

            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(inv));
            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());

            List<MedicineStockResponse> result = medicineStockService.getAvailableForUser(2L);

            assertThat(result.get(0).getUsername()).isEqualTo("john.doe");
            assertThat(result.get(0).getMedicineName()).isEqualTo("Shield FX Vial 10 ml");
        }

        @Test
        @DisplayName("shows the CurrentStockCalculator's reconstructed quantity, not the raw MedicineStock.quantity field")
        void usesReconstructedQuantityNotRawField() {
            // Raw ledger says 11 (e.g. drifted from true history, or includes in-transit stock
            // not yet arrived) — the reconstructed/settled figure is 8. The dispatch form's
            // "max" must show 8, matching what the daily report would show for the same bucket.
            MedicineStock regular = MedicineStock.builder().id(1L).user(user).medicine(medicine)
                    .quantity(BigDecimal.valueOf(11)).medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK).build();

            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(regular));
            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(currentStockCalculator.settledQuantitiesForUser(2L))
                    .thenReturn(Map.of("1|REGULAR_MEDICINE_STOCK", BigDecimal.valueOf(8)));

            List<MedicineStockResponse> result = medicineStockService.getAvailableForUser(2L);

            assertThat(result.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(8));
        }

        @Test
        @DisplayName("shows 0 when the calculator has no reconstructed data for the bucket, even if raw quantity is positive")
        void showsZeroWhenNoReconstructedData() {
            MedicineStock regular = MedicineStock.builder().id(1L).user(user).medicine(medicine)
                    .quantity(BigDecimal.valueOf(10)).medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK).build();

            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(regular));
            when(medicineStockRepository.findAvailableByUserIdAndType(2L, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());

            List<MedicineStockResponse> result = medicineStockService.getAvailableForUser(2L);

            assertThat(result.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ── getAdjustments ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAdjustments")
    class GetAdjustments {

        private MedicineStockAdjustment makeAdj(String type, int qty) {
            return MedicineStockAdjustment.builder()
                    .id(10L).user(user).medicine(medicine).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType(type).note("Test note for adjustment")
                    .inTransit(false).wasInTransit(false).transitDays(2)
                    .internalMovement(false)
                    .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                    .adjustedBy(adminUser)
                    .build();
        }

        @Test
        @DisplayName("returns list of adjustment responses for date range")
        void returnsAdjustmentsForDateRange() {
            when(medicineStockAdjustmentRepository.findWithDetailsBetween(any(), any()))
                    .thenReturn(List.of(makeAdj("ADD", 10)));

            List<MedicineStockAdjustmentResponse> result =
                    medicineStockService.getAdjustments(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 6));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("john.doe");
            assertThat(result.get(0).getAdjustmentType()).isEqualTo("ADD");
            assertThat(result.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        @DisplayName("maps adjustedByUsername from adjustedBy entity")
        void mapsAdjustedByUsername() {
            when(medicineStockAdjustmentRepository.findWithDetailsBetween(any(), any()))
                    .thenReturn(List.of(makeAdj("ADD", 5)));

            List<MedicineStockAdjustmentResponse> result =
                    medicineStockService.getAdjustments(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));

            assertThat(result.get(0).getAdjustedByUsername()).isEqualTo("admin");
        }

        @Test
        @DisplayName("returns empty list when no adjustments in range")
        void returnsEmptyList() {
            when(medicineStockAdjustmentRepository.findWithDetailsBetween(any(), any()))
                    .thenReturn(List.of());

            List<MedicineStockAdjustmentResponse> result =
                    medicineStockService.getAdjustments(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

            assertThat(result).isEmpty();
        }
    }

    // ── deleteAdjustment ──────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAdjustment")
    class DeleteAdjustment {

        private MedicineStockAdjustment addAdj(int qty) {
            return MedicineStockAdjustment.builder()
                    .id(20L).user(user).medicine(medicine).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType("ADD").note("Test ADD")
                    .inTransit(false).wasInTransit(false).transitDays(2)
                    .internalMovement(false)
                    .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now())
                    .build();
        }

        private MedicineStockAdjustment reduceAdj(int qty) {
            return MedicineStockAdjustment.builder()
                    .id(21L).user(user).medicine(medicine).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType("REDUCE").note("Test REDUCE")
                    .inTransit(false).wasInTransit(false).transitDays(2)
                    .internalMovement(false)
                    .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("deleting ADD adjustment reduces medicineStock quantity")
        void deletingAddReducesMedicineStock() {
            when(medicineStockAdjustmentRepository.findById(20L)).thenReturn(Optional.of(addAdj(10)));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock)); // qty = 50

            medicineStockService.deleteAdjustment(20L);

            verify(medicineStockRepository).save(argThat(inv -> inv.getQuantity().compareTo(BigDecimal.valueOf(40)) == 0));
            verify(medicineStockAdjustmentRepository).deleteById(20L);
        }

        @Test
        @DisplayName("deleting REDUCE adjustment restores medicineStock quantity")
        void deletingReduceRestoresMedicineStock() {
            when(medicineStockAdjustmentRepository.findById(21L)).thenReturn(Optional.of(reduceAdj(10)));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock)); // qty = 50

            medicineStockService.deleteAdjustment(21L);

            verify(medicineStockRepository).save(argThat(inv -> inv.getQuantity().compareTo(BigDecimal.valueOf(60)) == 0));
            verify(medicineStockAdjustmentRepository).deleteById(21L);
        }

        @Test
        @DisplayName("deleting ADD clamps medicineStock to 0 if reversal would go negative")
        void deletingAddClampsToZero() {
            medicineStock.setQuantity(BigDecimal.valueOf(5)); // only 5 remains, but adjustment was for 10
            when(medicineStockAdjustmentRepository.findById(20L)).thenReturn(Optional.of(addAdj(10)));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.of(medicineStock));

            medicineStockService.deleteAdjustment(20L);

            verify(medicineStockRepository).save(argThat(inv -> inv.getQuantity().compareTo(BigDecimal.ZERO) == 0));
        }

        @Test
        @DisplayName("skips medicineStock update when medicineStock record not found")
        void skipsMedicineStockUpdateWhenNotFound() {
            when(medicineStockAdjustmentRepository.findById(20L)).thenReturn(Optional.of(addAdj(10)));
            when(medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(any(), any(), any()))
                    .thenReturn(Optional.empty());

            medicineStockService.deleteAdjustment(20L);

            verify(medicineStockRepository, never()).save(any());
            verify(medicineStockAdjustmentRepository).deleteById(20L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when adjustment does not exist")
        void throwsWhenAdjustmentNotFound() {
            when(medicineStockAdjustmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> medicineStockService.deleteAdjustment(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(medicineStockAdjustmentRepository, never()).deleteById(any());
        }
    }

    // ── getAll ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("returns all medicineStock items mapped to MedicineStockResponse")
        void returnsAllMapped() {
            when(medicineStockRepository.findAll()).thenReturn(List.of(medicineStock));

            List<MedicineStockResponse> result = medicineStockService.getAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("john.doe");
            assertThat(result.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(50));
        }

        @Test
        @DisplayName("returns empty list when no medicineStock exists")
        void returnsEmptyList() {
            when(medicineStockRepository.findAll()).thenReturn(List.of());

            List<MedicineStockResponse> result = medicineStockService.getAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maps medicine name and pharma company into response")
        void mapsMedicineAndPharma() {
            when(medicineStockRepository.findAll()).thenReturn(List.of(medicineStock));

            List<MedicineStockResponse> result = medicineStockService.getAll();

            assertThat(result.get(0).getMedicineName()).isEqualTo("Shield FX Vial 10 ml");
            assertThat(result.get(0).getPharmaName()).isEqualTo("Shield FX");
            assertThat(result.get(0).getPrice()).isEqualTo(4000);
        }

        @Test
        @DisplayName("sets specUnit to 'ml' for VIAL medicine type")
        void setsVialSpecUnit() {
            when(medicineStockRepository.findAll()).thenReturn(List.of(medicineStock));

            List<MedicineStockResponse> result = medicineStockService.getAll();

            assertThat(result.get(0).getSpecUnit()).isEqualTo("ml");
        }

        @Test
        @DisplayName("sets specUnit to 'mg (10 Tablets)' for TABLET medicine type")
        void setsTabletSpecUnit() {
            Medicine tablet = Medicine.builder().id(2L).name("Shield FX Tablet 25 mg")
                    .type(Medicine.MedicineType.TABLET).specification(25.0)
                    .price(4000).pharmaCompany(pharma).active(true).build();
            MedicineStock tabletInv = MedicineStock.builder().id(11L).user(user).medicine(tablet)
                    .quantity(BigDecimal.valueOf(30)).medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK).build();

            when(medicineStockRepository.findAll()).thenReturn(List.of(tabletInv));

            List<MedicineStockResponse> result = medicineStockService.getAll();

            assertThat(result.get(0).getSpecUnit()).isEqualTo("mg (10 Tablets)");
        }
    }
}
