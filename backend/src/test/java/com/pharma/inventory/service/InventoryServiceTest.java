package com.pharma.inventory.service;

import com.pharma.inventory.dto.AdjustInventoryRequest;
import com.pharma.inventory.dto.InventoryResponse;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.exception.InsufficientInventoryException;
import com.pharma.inventory.exception.ResourceNotFoundException;
import com.pharma.inventory.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryAdjustmentRepository inventoryAdjustmentRepository;
    @Mock UserRepository userRepository;
    @Mock MedicineRepository medicineRepository;

    @InjectMocks InventoryService inventoryService;

    private User user;
    private User adminUser;
    private Medicine medicine;
    private PharmaCompany pharma;
    private Inventory inventory;

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

        inventory = Inventory.builder()
                .id(10L).user(user).medicine(medicine).quantity(50)
                .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK).build();
    }

    private AdjustInventoryRequest addReq(int qty) {
        AdjustInventoryRequest r = new AdjustInventoryRequest();
        r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(qty);
        r.setAdjustmentType("ADD"); r.setNote("Adding stock for test");
        return r;
    }

    private AdjustInventoryRequest reduceReq(int qty) {
        AdjustInventoryRequest r = new AdjustInventoryRequest();
        r.setUserId(2L); r.setMedicineId(1L); r.setQuantity(qty);
        r.setAdjustmentType("REDUCE"); r.setNote("Reducing stock for test");
        return r;
    }

    // ── adjustInventory ────────────────────────────────────────────────────

    @Nested
    @DisplayName("adjustInventory")
    class AdjustInventory {

        @Test
        @DisplayName("ADD increases quantity and returns updated response")
        void addIncreasesQuantity() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            InventoryResponse result = inventoryService.adjustInventory(addReq(10), "admin");

            assertThat(result.getQuantity()).isEqualTo(60);
        }

        @Test
        @DisplayName("REDUCE decreases quantity and returns updated response")
        void reduceDecreasesQuantity() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            InventoryResponse result = inventoryService.adjustInventory(reduceReq(20), "admin");

            assertThat(result.getQuantity()).isEqualTo(30);
        }

        @Test
        @DisplayName("creates new Inventory record when none exists for user+medicine+type")
        void createsNewInventoryWhenNotFound() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            InventoryResponse result = inventoryService.adjustInventory(addReq(5), "admin");

            assertThat(result.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            AdjustInventoryRequest req = addReq(5);
            req.setUserId(999L);

            assertThatThrownBy(() -> inventoryService.adjustInventory(req, "admin"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(inventoryRepository, medicineRepository);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when target user is ADMIN")
        void throwsWhenTargetUserIsAdmin() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
            AdjustInventoryRequest req = addReq(5);
            req.setUserId(1L);

            assertThatThrownBy(() -> inventoryService.adjustInventory(req, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Admin user cannot hold inventory");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when medicine not found")
        void throwsWhenMedicineNotFound() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(999L)).thenReturn(Optional.empty());
            AdjustInventoryRequest req = addReq(5);
            req.setMedicineId(999L);

            assertThatThrownBy(() -> inventoryService.adjustInventory(req, "admin"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws InsufficientInventoryException when reducing below available quantity")
        void throwsInsufficientInventory() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory)); // quantity = 50

            assertThatThrownBy(() -> inventoryService.adjustInventory(reduceReq(100), "admin"))
                    .isInstanceOf(InsufficientInventoryException.class);
        }

        @Test
        @DisplayName("does not throw when reducing exactly the available quantity")
        void doesNotThrowWhenReducingExactAmount() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory)); // quantity = 50
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            InventoryResponse result = inventoryService.adjustInventory(reduceReq(50), "admin");

            assertThat(result.getQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("saves InventoryAdjustment audit record with correct values")
        void savesAuditRecord() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            inventoryService.adjustInventory(addReq(7), "admin");

            verify(inventoryAdjustmentRepository).save(argThat(adj ->
                    adj.getQuantity() == 7 &&
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
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustInventoryRequest req = addReq(5);
            req.setAdjustmentDate(LocalDate.of(2026, 1, 15));

            inventoryService.adjustInventory(req, "admin");

            verify(inventoryAdjustmentRepository).save(argThat(adj ->
                    adj.getAdjustedAt().toLocalDate().equals(LocalDate.of(2026, 1, 15))
            ));
        }

        @Test
        @DisplayName("records inTransit=true in audit record")
        void recordsInTransitTrue() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustInventoryRequest req = addReq(5);
            req.setInTransit(true);

            inventoryService.adjustInventory(req, "admin");

            verify(inventoryAdjustmentRepository).save(argThat(InventoryAdjustment::isInTransit));
        }

        @Test
        @DisplayName("records internalMovement=true in audit record")
        void recordsInternalMovementTrue() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustInventoryRequest req = addReq(5);
            req.setInternalMovement(true);

            inventoryService.adjustInventory(req, "admin");

            verify(inventoryAdjustmentRepository).save(argThat(InventoryAdjustment::isInternalMovement));
        }

        @Test
        @DisplayName("stores ADMIN_MEDICINE_STOCK type when specified in request")
        void storesAdminStockType() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(
                    eq(2L), eq(1L), eq(Inventory.InventoryType.ADMIN_MEDICINE_STOCK)))
                    .thenReturn(Optional.empty());
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustInventoryRequest req = addReq(5);
            req.setInventoryType("ADMIN_MEDICINE_STOCK");

            InventoryResponse result = inventoryService.adjustInventory(req, "admin");

            assertThat(result.getInventoryType()).isEqualTo("ADMIN_MEDICINE_STOCK");
        }

        @Test
        @DisplayName("defaults to REGULAR_MEDICINE_STOCK when invalid inventoryType is given")
        void defaultsToRegularWhenInvalidType() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(
                    eq(2L), eq(1L), eq(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustInventoryRequest req = addReq(5);
            req.setInventoryType("NOT_A_REAL_TYPE");

            inventoryService.adjustInventory(req, "admin");

            verify(inventoryRepository).findByUserIdAndMedicineIdAndInventoryType(
                    2L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        }

        @Test
        @DisplayName("sets lastNote on inventory record from request note")
        void setsLastNoteOnInventory() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

            AdjustInventoryRequest req = addReq(5);
            req.setNote("Custom note for this adjustment");

            inventoryService.adjustInventory(req, "admin");

            verify(inventoryRepository).save(argThat(inv ->
                    "Custom note for this adjustment".equals(inv.getLastNote())
            ));
        }

        @Test
        @DisplayName("maps adjustedBy to the admin performing the operation")
        void mapsAdjustedByUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(any(), any(), any()))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            inventoryService.adjustInventory(addReq(3), "admin");

            verify(inventoryAdjustmentRepository).save(argThat(adj ->
                    adj.getAdjustedBy() != null && adj.getAdjustedBy().getId().equals(1L)
            ));
        }
    }

    // ── getAvailableForUser ────────────────────────────────────────────────

    @Nested
    @DisplayName("getAvailableForUser")
    class GetAvailableForUser {

        @Test
        @DisplayName("returns combined regular and admin stock")
        void returnsBothStockTypes() {
            Inventory regular = Inventory.builder().id(1L).user(user).medicine(medicine)
                    .quantity(10).inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK).build();
            Inventory admin = Inventory.builder().id(2L).user(user).medicine(medicine)
                    .quantity(5).inventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK).build();

            when(inventoryRepository.findAvailableByUserIdAndType(2L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(regular));
            when(inventoryRepository.findAvailableByUserIdAndType(2L, Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(admin));

            List<InventoryResponse> result = inventoryService.getAvailableForUser(2L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("regular stock appears before admin stock in result list")
        void regularBeforeAdminStock() {
            Inventory regular = Inventory.builder().id(1L).user(user).medicine(medicine)
                    .quantity(10).inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK).build();
            Inventory admin = Inventory.builder().id(2L).user(user).medicine(medicine)
                    .quantity(5).inventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK).build();

            when(inventoryRepository.findAvailableByUserIdAndType(2L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(regular));
            when(inventoryRepository.findAvailableByUserIdAndType(2L, Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(admin));

            List<InventoryResponse> result = inventoryService.getAvailableForUser(2L);

            assertThat(result.get(0).getInventoryType()).isEqualTo("REGULAR_MEDICINE_STOCK");
            assertThat(result.get(1).getInventoryType()).isEqualTo("ADMIN_MEDICINE_STOCK");
        }

        @Test
        @DisplayName("returns empty list when user has no inventory")
        void returnsEmptyWhenNoInventory() {
            when(inventoryRepository.findAvailableByUserIdAndType(any(), any())).thenReturn(List.of());

            List<InventoryResponse> result = inventoryService.getAvailableForUser(2L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maps username correctly in response")
        void mapsUsernameInResponse() {
            Inventory inv = Inventory.builder().id(1L).user(user).medicine(medicine)
                    .quantity(10).inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK).build();

            when(inventoryRepository.findAvailableByUserIdAndType(2L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(inv));
            when(inventoryRepository.findAvailableByUserIdAndType(2L, Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());

            List<InventoryResponse> result = inventoryService.getAvailableForUser(2L);

            assertThat(result.get(0).getUsername()).isEqualTo("john.doe");
            assertThat(result.get(0).getMedicineName()).isEqualTo("Shield FX Vial 10 ml");
        }
    }

    // ── getAll ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("returns all inventory items mapped to InventoryResponse")
        void returnsAllMapped() {
            when(inventoryRepository.findAll()).thenReturn(List.of(inventory));

            List<InventoryResponse> result = inventoryService.getAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("john.doe");
            assertThat(result.get(0).getQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("returns empty list when no inventory exists")
        void returnsEmptyList() {
            when(inventoryRepository.findAll()).thenReturn(List.of());

            List<InventoryResponse> result = inventoryService.getAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maps medicine name and pharma company into response")
        void mapsMedicineAndPharma() {
            when(inventoryRepository.findAll()).thenReturn(List.of(inventory));

            List<InventoryResponse> result = inventoryService.getAll();

            assertThat(result.get(0).getMedicineName()).isEqualTo("Shield FX Vial 10 ml");
            assertThat(result.get(0).getPharmaName()).isEqualTo("Shield FX");
            assertThat(result.get(0).getPrice()).isEqualTo(4000);
        }

        @Test
        @DisplayName("sets specUnit to 'ml' for VIAL medicine type")
        void setsVialSpecUnit() {
            when(inventoryRepository.findAll()).thenReturn(List.of(inventory));

            List<InventoryResponse> result = inventoryService.getAll();

            assertThat(result.get(0).getSpecUnit()).isEqualTo("ml");
        }

        @Test
        @DisplayName("sets specUnit to 'mg (10 Tablets)' for TABLET medicine type")
        void setsTabletSpecUnit() {
            Medicine tablet = Medicine.builder().id(2L).name("Shield FX Tablet 25 mg")
                    .type(Medicine.MedicineType.TABLET).specification(25.0)
                    .price(4000).pharmaCompany(pharma).active(true).build();
            Inventory tabletInv = Inventory.builder().id(11L).user(user).medicine(tablet)
                    .quantity(30).inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK).build();

            when(inventoryRepository.findAll()).thenReturn(List.of(tabletInv));

            List<InventoryResponse> result = inventoryService.getAll();

            assertThat(result.get(0).getSpecUnit()).isEqualTo("mg (10 Tablets)");
        }
    }
}
