package com.pharma.inventory.service;

import com.pharma.inventory.dto.ApprovalRequest;
import com.pharma.inventory.dto.ScreenshotDto;
import com.pharma.inventory.dto.TransactionRequest;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.dto.UpdateTransactionRequest;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.entity.Transaction.TransactionStatus;
import com.pharma.inventory.exception.InsufficientInventoryException;
import com.pharma.inventory.exception.InvalidStateTransitionException;
import com.pharma.inventory.exception.ResourceNotFoundException;
import com.pharma.inventory.mapper.TransactionMapper;
import com.pharma.inventory.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService")
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock TransactionScreenshotRepository screenshotRepository;
    @Mock UserRepository        userRepository;
    @Mock MedicineRepository    medicineRepository;
    @Mock InventoryRepository   inventoryRepository;
    @Mock InventoryAdjustmentRepository inventoryAdjustmentRepository;
    @Mock TransactionMapper     transactionMapper;

    @InjectMocks TransactionService transactionService;

    private User         regularUser;
    private User         adminUser;
    private PharmaCompany pharma;
    private Medicine     medicine;
    private Inventory    inventory;

    @BeforeEach
    void setUp() {
        pharma = new PharmaCompany();
        pharma.setId(1L); pharma.setName("FIP Shield");

        medicine = new Medicine();
        medicine.setId(1L); medicine.setName("FIP Shield Vial");
        medicine.setType(Medicine.MedicineType.VIAL);
        medicine.setSpecification(10.0); medicine.setPharmaCompany(pharma);

        regularUser = new User();
        regularUser.setId(1L); regularUser.setUsername("john.doe");
        regularUser.setFullName("John Doe");

        adminUser = new User();
        adminUser.setId(2L); adminUser.setUsername("admin");
        adminUser.setFullName("Admin User");

        inventory = new Inventory();
        inventory.setId(1L); inventory.setUser(regularUser);
        inventory.setMedicine(medicine); inventory.setQuantity(50);

        // Default: no stock in transit. Individual tests override this to exercise the
        // settled-quantity exclusion.
        lenient().when(inventoryAdjustmentRepository.findActiveInTransitFor(any(), any(), any()))
                .thenReturn(List.of());
    }

    private InventoryAdjustment activeInTransitAdj(int qty) {
        return InventoryAdjustment.builder()
                .id(99L).user(regularUser).medicine(medicine).quantity(qty)
                .adjustmentType("ADD").inTransit(true).wasInTransit(true).transitDays(2)
                .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                .adjustedAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private TransactionRequest buildReq(String notes) {
        TransactionRequest r = new TransactionRequest();
        r.setMedicineId(1L); r.setQuantity(10); r.setNotes(notes);
        return r;
    }

    private Transaction savedTx(TransactionRequest req) {
        Transaction t = Transaction.builder()
                .id(1L).submittedBy(regularUser).medicine(medicine)
                .quantity(req.getQuantity()).status(TransactionStatus.PENDING)
                .notes(req.getNotes()).build();
        t.setSubmittedAt(LocalDateTime.now());
        return t;
    }

    private TransactionResponse stubResponse(Transaction t) {
        TransactionResponse r = new TransactionResponse();
        r.setId(t.getId()); r.setStatus(t.getStatus().name());
        r.setQuantity(t.getQuantity()); r.setNotes(t.getNotes());
        return r;
    }

    // ── submit() ──────────────────────────────────────────────────────

    @Nested @DisplayName("submit()")
    class Submit {

        @Test @DisplayName("creates PENDING transaction and deducts inventory")
        void submit_valid_createsPendingAndDeducts() {
            TransactionRequest req = buildReq("Dispatched to clinic B for FIP treatment");
            Transaction tx = savedTx(req);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);
            when(transactionRepository.save(any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(stubResponse(tx));

            TransactionResponse res = transactionService.submit(req, "john.doe");

            assertThat(res.getStatus()).isEqualTo("PENDING");
            verify(inventoryRepository).save(argThat(i -> i.getQuantity() == 40));
        }

        @Test @DisplayName("saves TransactionScreenshot entries when screenshots provided")
        void submit_withScreenshots_savesScreenshotEntities() {
            String b64 = Base64.getEncoder().encodeToString("png".getBytes());
            TransactionRequest req = buildReq("Payment confirmed, dispatching now");
            req.setPaymentScreenshots(List.of(b64));
            req.setPaymentScreenshotTypes(List.of("image/png"));
            Transaction tx = savedTx(req);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);
            when(transactionRepository.save(any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(stubResponse(tx));

            transactionService.submit(req, "john.doe");

            verify(screenshotRepository).save(argThat(s ->
                    b64.equals(s.getData()) && "image/png".equals(s.getMimeType()) && s.getDisplayOrder() == 0));
        }

        @Test @DisplayName("saves multiple screenshots in display order")
        void submit_withMultipleScreenshots_savesAllInOrder() {
            String b64a = Base64.getEncoder().encodeToString("png1".getBytes());
            String b64b = Base64.getEncoder().encodeToString("jpg2".getBytes());
            TransactionRequest req = buildReq("Two screenshots dispatched here");
            req.setPaymentScreenshots(List.of(b64a, b64b));
            req.setPaymentScreenshotTypes(List.of("image/png", "image/jpeg"));
            Transaction tx = savedTx(req);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);
            when(transactionRepository.save(any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(stubResponse(tx));

            transactionService.submit(req, "john.doe");

            verify(screenshotRepository, times(2)).save(any());
            verify(screenshotRepository).save(argThat(s -> s.getDisplayOrder() == 0 && b64a.equals(s.getData())));
            verify(screenshotRepository).save(argThat(s -> s.getDisplayOrder() == 1 && b64b.equals(s.getData())));
        }

        @Test @DisplayName("throws ResourceNotFoundException when user not found")
        void submit_userNotFound_throwsResourceNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> transactionService.submit(buildReq("Valid note here"), "ghost"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("ghost");
        }

        @Test @DisplayName("throws ResourceNotFoundException when medicine not found")
        void submit_medicineNotFound_throwsResourceNotFound() {
            TransactionRequest req = buildReq("Valid adjustment note");
            req.setMedicineId(99L);
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.submit(req, "john.doe"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Medicine").hasMessageContaining("99");
        }

        @Test @DisplayName("throws ResourceNotFoundException when no inventory for medicine")
        void submit_inventoryNotFound_throwsResourceNotFound() {
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transactionService.submit(buildReq("Valid clinic note"), "john.doe"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Inventory");
        }

        @Test @DisplayName("throws InsufficientInventoryException when qty exceeds stock")
        void submit_insufficientStock_throwsInsufficientInventory() {
            inventory.setQuantity(5);
            TransactionRequest req = buildReq("Clinic dispatch today");
            req.setQuantity(10);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));

            assertThatThrownBy(() -> transactionService.submit(req, "john.doe"))
                    .isInstanceOf(InsufficientInventoryException.class)
                    .hasMessageContaining("5").hasMessageContaining("10");
        }

        @Test @DisplayName("throws IllegalArgumentException when notes are blank")
        void submit_blankNotes_throwsIllegalArgument() {
            TransactionRequest req = buildReq("  ");

            assertThatThrownBy(() -> transactionService.submit(req, "john.doe"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("adjustment note is required");
        }

        @Test @DisplayName("throws IllegalArgumentException when notes are too short")
        void submit_notesTooShort_throwsIllegalArgument() {
            TransactionRequest req = buildReq("Hi");

            assertThatThrownBy(() -> transactionService.submit(req, "john.doe"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("5 and 500");
        }

        @Test @DisplayName("throws IllegalArgumentException when notes exceed 500 chars")
        void submit_notesTooLong_throwsIllegalArgument() {
            TransactionRequest req = buildReq("x".repeat(501));

            assertThatThrownBy(() -> transactionService.submit(req, "john.doe"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("5 and 500");
        }

        @Test @DisplayName("accepts notes at exact minimum length (5 chars)")
        void submit_notesExactMin_succeeds() {
            TransactionRequest req = buildReq("Hello");
            Transaction tx = savedTx(req);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);
            when(transactionRepository.save(any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(stubResponse(tx));

            assertThatNoException().isThrownBy(() -> transactionService.submit(req, "john.doe"));
        }

        @Test @DisplayName("accepts notes at exact maximum length (500 chars)")
        void submit_notesExactMax_succeeds() {
            TransactionRequest req = buildReq("x".repeat(500));
            Transaction tx = savedTx(req);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);
            when(transactionRepository.save(any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(stubResponse(tx));

            assertThatNoException().isThrownBy(() -> transactionService.submit(req, "john.doe"));
        }

        @Test @DisplayName("throws InsufficientInventoryException when requested exceeds settled stock, even though raw quantity is enough")
        void submit_exceedsSettledStock_throwsInsufficientInventoryDespiteRawQuantity() {
            inventory.setQuantity(11); // raw includes 3 units still in transit — only 8 settled
            TransactionRequest req = buildReq("Clinic dispatch today please");
            req.setQuantity(11);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryAdjustmentRepository.findActiveInTransitFor(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(activeInTransitAdj(3)));

            assertThatThrownBy(() -> transactionService.submit(req, "john.doe"))
                    .isInstanceOf(InsufficientInventoryException.class)
                    .hasMessageContaining("8").hasMessageContaining("11");
            verify(inventoryRepository, never()).save(any());
        }

        @Test @DisplayName("succeeds when requested equals settled (raw minus in-transit) quantity")
        void submit_exactlySettledStock_succeeds() {
            inventory.setQuantity(11); // raw 11, 3 in transit, 8 settled
            TransactionRequest req = buildReq("Clinic dispatch of settled stock");
            req.setQuantity(8);
            Transaction tx = savedTx(req);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryAdjustmentRepository.findActiveInTransitFor(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(activeInTransitAdj(3)));
            when(inventoryRepository.save(any())).thenReturn(inventory);
            when(transactionRepository.save(any())).thenReturn(tx);
            when(transactionMapper.toResponse(tx)).thenReturn(stubResponse(tx));

            assertThatNoException().isThrownBy(() -> transactionService.submit(req, "john.doe"));
            verify(inventoryRepository).save(argThat(i -> i.getQuantity() == 3)); // 11 - 8
        }
    }

    // ── getAllPaged() ─────────────────────────────────────────────────

    @Nested @DisplayName("getAllPaged()")
    class GetAllPaged {

        @Test @DisplayName("returns empty page when no transactions exist")
        void getAllPaged_noTransactions_returnsEmpty() {
            when(transactionRepository.findAllIds(any(Pageable.class)))
                    .thenReturn(Page.empty());
            Page<TransactionResponse> result = transactionService.getAllPaged("ALL", 0, 20);
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test @DisplayName("routes to findIdsByStatus when status is not ALL")
        void getAllPaged_withStatus_routesToFilteredQuery() {
            when(transactionRepository.findIdsByStatus(
                    eq(Transaction.TransactionStatus.PENDING), any(Pageable.class)))
                    .thenReturn(Page.empty());
            transactionService.getAllPaged("PENDING", 0, 20);
            verify(transactionRepository).findIdsByStatus(
                    eq(Transaction.TransactionStatus.PENDING), any(Pageable.class));
            verify(transactionRepository, never()).findAllIds(any());
        }

        @Test @DisplayName("maps all transactions via mapper")
        void getAllPaged_multipleTransactions_mapsAll() {
            Transaction t1 = savedTx(buildReq("Note one here for dispatch"));
            Transaction t2 = savedTx(buildReq("Note two for the clinic today"));
            t2.setId(2L);
            TransactionResponse r1 = stubResponse(t1);
            TransactionResponse r2 = stubResponse(t2);

            when(transactionRepository.findAllIds(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(1L, 2L), PageRequest.of(0, 20), 2));
            when(transactionRepository.findByIdsWithDetails(List.of(1L, 2L)))
                    .thenReturn(List.of(t1, t2));
            when(transactionMapper.toResponse(t1)).thenReturn(r1);
            when(transactionMapper.toResponse(t2)).thenReturn(r2);

            Page<TransactionResponse> result = transactionService.getAllPaged("ALL", 0, 20);
            assertThat(result.getContent()).hasSize(2).containsExactly(r1, r2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test @DisplayName("returns screenshots in response when present")
        void getAllPaged_txWithScreenshots_includesScreenshotsInResponse() {
            String b64 = Base64.getEncoder().encodeToString("img".getBytes());
            Transaction tx = savedTx(buildReq("Dispatch with payment proof"));
            TransactionResponse resp = stubResponse(tx);
            resp.setScreenshots(List.of(new ScreenshotDto(b64, "image/png")));

            when(transactionRepository.findAllIds(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(1L), PageRequest.of(0, 20), 1));
            when(transactionRepository.findByIdsWithDetails(List.of(1L)))
                    .thenReturn(List.of(tx));
            when(transactionMapper.toResponse(tx)).thenReturn(resp);

            Page<TransactionResponse> result = transactionService.getAllPaged("ALL", 0, 20);
            assertThat(result.getContent().get(0).getScreenshots()).hasSize(1);
            assertThat(result.getContent().get(0).getScreenshots().get(0).getData()).isEqualTo(b64);
            assertThat(result.getContent().get(0).getScreenshots().get(0).getMimeType()).isEqualTo("image/png");
        }

        @Test @DisplayName("totalElements reflects full count across all pages")
        void getAllPaged_secondPage_totalElementsMatchesFullCount() {
            Transaction tx = savedTx(buildReq("Dispatch page two here"));
            tx.setId(21L);
            TransactionResponse resp = stubResponse(tx);

            when(transactionRepository.findAllIds(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(21L), PageRequest.of(1, 20), 21));
            when(transactionRepository.findByIdsWithDetails(List.of(21L)))
                    .thenReturn(List.of(tx));
            when(transactionMapper.toResponse(tx)).thenReturn(resp);

            Page<TransactionResponse> result = transactionService.getAllPaged("ALL", 1, 20);
            assertThat(result.getTotalElements()).isEqualTo(21);
            assertThat(result.getNumber()).isEqualTo(1);
        }
    }

    // ── getByUserPaged() ──────────────────────────────────────────────

    @Nested @DisplayName("getByUserPaged()")
    class GetByUserPaged {

        @Test @DisplayName("returns empty page when user has no transactions")
        void getByUserPaged_noTransactions_returnsEmpty() {
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(transactionRepository.findIdsByUser(eq(regularUser), any(Pageable.class)))
                    .thenReturn(Page.empty());
            Page<TransactionResponse> result = transactionService.getByUserPaged("john.doe", 0, 20);
            assertThat(result.getContent()).isEmpty();
        }

        @Test @DisplayName("throws ResourceNotFoundException when user not found")
        void getByUserPaged_unknownUser_throwsResourceNotFound() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> transactionService.getByUserPaged("nobody", 0, 20))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User").hasMessageContaining("nobody");
        }

        @Test @DisplayName("returns empty screenshots list when no screenshot uploaded")
        void getByUserPaged_noScreenshot_emptyScreenshotsInResponse() {
            Transaction tx = savedTx(buildReq("Standard dispatch order five"));
            tx.setId(1L);
            TransactionResponse resp = stubResponse(tx);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(transactionRepository.findIdsByUser(eq(regularUser), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(1L), PageRequest.of(0, 20), 1));
            when(transactionRepository.findByIdsWithDetails(List.of(1L)))
                    .thenReturn(List.of(tx));
            when(transactionMapper.toResponse(tx)).thenReturn(resp);

            Page<TransactionResponse> result = transactionService.getByUserPaged("john.doe", 0, 20);
            assertThat(result.getContent().get(0).getScreenshots()).isEmpty();
        }
    }

    // ── approve() ─────────────────────────────────────────────────────

    @Nested @DisplayName("approve()")
    class Approve {

        private Transaction pendingTx;

        @BeforeEach
        void setup() {
            pendingTx = Transaction.builder()
                    .id(1L).submittedBy(regularUser).medicine(medicine)
                    .quantity(10).status(TransactionStatus.PENDING)
                    .notes("Clinic dispatch confirmed").build();
            pendingTx.setSubmittedAt(LocalDateTime.now());
        }

        @Test @DisplayName("sets status APPROVED and records approver")
        void approve_valid_setsApproved() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionMapper.toResponse(any())).thenAnswer(inv -> {
                Transaction t = inv.getArgument(0);
                TransactionResponse r = new TransactionResponse();
                r.setStatus(t.getStatus().name()); return r;
            });

            ApprovalRequest req = new ApprovalRequest(); req.setApproved(true);
            TransactionResponse res = transactionService.approve(1L, req, "admin");

            assertThat(res.getStatus()).isEqualTo("APPROVED");
        }

        @Test @DisplayName("sets status REJECTED and restores inventory")
        void reject_valid_setsRejectedAndRestoresInventory() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionMapper.toResponse(any())).thenAnswer(inv -> {
                Transaction t = inv.getArgument(0);
                TransactionResponse r = new TransactionResponse();
                r.setStatus(t.getStatus().name()); return r;
            });

            ApprovalRequest req = new ApprovalRequest(); req.setApproved(false);
            TransactionResponse res = transactionService.approve(1L, req, "admin");

            assertThat(res.getStatus()).isEqualTo("REJECTED");
            verify(inventoryRepository).save(argThat(i -> i.getQuantity() == 60)); // 50+10
        }

        @Test @DisplayName("throws ResourceNotFoundException when transaction not found")
        void approve_txNotFound_throwsResourceNotFound() {
            when(transactionRepository.findById(99L)).thenReturn(Optional.empty());
            ApprovalRequest req = new ApprovalRequest(); req.setApproved(true);

            assertThatThrownBy(() -> transactionService.approve(99L, req, "admin"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction").hasMessageContaining("99");
        }

        @Test @DisplayName("throws InvalidStateTransitionException when already APPROVED")
        void approve_alreadyApproved_throwsInvalidStateTransition() {
            pendingTx.setStatus(TransactionStatus.APPROVED);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            ApprovalRequest req = new ApprovalRequest(); req.setApproved(true);

            assertThatThrownBy(() -> transactionService.approve(1L, req, "admin"))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("APPROVED");
        }

        @Test @DisplayName("throws InvalidStateTransitionException when already REJECTED")
        void approve_alreadyRejected_throwsInvalidStateTransition() {
            pendingTx.setStatus(TransactionStatus.REJECTED);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            ApprovalRequest req = new ApprovalRequest(); req.setApproved(false);

            assertThatThrownBy(() -> transactionService.approve(1L, req, "admin"))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("REJECTED");
        }

        @Test @DisplayName("throws ResourceNotFoundException when admin user not found")
        void approve_adminNotFound_throwsResourceNotFound() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("ghost-admin")).thenReturn(Optional.empty());
            ApprovalRequest req = new ApprovalRequest(); req.setApproved(true);

            assertThatThrownBy(() -> transactionService.approve(1L, req, "ghost-admin"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User").hasMessageContaining("ghost-admin");
        }

        @Test @DisplayName("preserves screenshots after approval")
        void approve_preservesScreenshots() {
            String b64 = Base64.getEncoder().encodeToString("img".getBytes());

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionMapper.toResponse(any())).thenAnswer(inv -> {
                TransactionResponse r = new TransactionResponse();
                r.setScreenshots(List.of(new ScreenshotDto(b64, "image/png")));
                return r;
            });

            ApprovalRequest req = new ApprovalRequest(); req.setApproved(true);
            TransactionResponse res = transactionService.approve(1L, req, "admin");

            assertThat(res.getScreenshots()).hasSize(1);
            assertThat(res.getScreenshots().get(0).getData()).isEqualTo(b64);
        }
    }

    // ── deleteTransaction() ───────────────────────────────────────────

    @Nested @DisplayName("deleteTransaction()")
    class DeleteTransaction {

        private Transaction pendingTx;

        @BeforeEach
        void setup() {
            pendingTx = Transaction.builder()
                    .id(1L).submittedBy(regularUser).medicine(medicine)
                    .quantity(10).status(TransactionStatus.PENDING)
                    .notes("Clinic dispatch confirmed")
                    .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                    .build();
            pendingTx.setSubmittedAt(LocalDateTime.now());
        }

        @Test @DisplayName("deletes PENDING transaction and restores inventory")
        void delete_pendingTx_restoresInventoryAndDeletes() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            transactionService.deleteTransaction(1L);

            verify(inventoryRepository).save(argThat(i -> i.getQuantity() == 60)); // 50+10
            verify(transactionRepository).delete(pendingTx);
        }

        @Test @DisplayName("deletes APPROVED transaction and restores inventory")
        void delete_approvedTx_restoresInventoryAndDeletes() {
            pendingTx.setStatus(TransactionStatus.APPROVED);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            transactionService.deleteTransaction(1L);

            verify(inventoryRepository).save(argThat(i -> i.getQuantity() == 60));
            verify(transactionRepository).delete(pendingTx);
        }

        @Test @DisplayName("deletes REJECTED transaction without restoring inventory")
        void delete_rejectedTx_doesNotRestoreInventory() {
            pendingTx.setStatus(TransactionStatus.REJECTED);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));

            transactionService.deleteTransaction(1L);

            verify(inventoryRepository, never()).save(any());
            verify(transactionRepository).delete(pendingTx);
        }

        @Test @DisplayName("throws ResourceNotFoundException when transaction not found")
        void delete_notFound_throwsResourceNotFound() {
            when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.deleteTransaction(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction").hasMessageContaining("99");
        }
    }

    // ── deleteOwnPending() ────────────────────────────────────────────

    @Nested @DisplayName("deleteOwnPending()")
    class DeleteOwnPending {

        private Transaction pendingTx;

        @BeforeEach
        void setup() {
            pendingTx = Transaction.builder()
                    .id(1L).submittedBy(regularUser).medicine(medicine)
                    .quantity(10).status(TransactionStatus.PENDING)
                    .notes("Clinic dispatch confirmed")
                    .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                    .build();
            pendingTx.setSubmittedAt(LocalDateTime.now());
        }

        @Test @DisplayName("owner deletes their own PENDING transaction and inventory is restored")
        void delete_ownPendingTx_restoresInventoryAndDeletes() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(1L, 1L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            transactionService.deleteOwnPending(1L, "john.doe");

            verify(inventoryRepository).save(argThat(i -> i.getQuantity() == 60)); // 50+10
            verify(transactionRepository).delete(pendingTx);
        }

        @Test @DisplayName("throws AccessDeniedException when the transaction belongs to another user")
        void delete_notOwner_throwsAccessDenied() {
            User otherUser = new User();
            otherUser.setId(99L); otherUser.setUsername("jane.doe");

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("jane.doe")).thenReturn(Optional.of(otherUser));

            assertThatThrownBy(() -> transactionService.deleteOwnPending(1L, "jane.doe"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(transactionRepository, never()).delete(any());
            verify(inventoryRepository, never()).save(any());
        }

        @Test @DisplayName("throws InvalidStateTransitionException when transaction is APPROVED")
        void delete_approvedTx_throwsInvalidStateTransition() {
            pendingTx.setStatus(TransactionStatus.APPROVED);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));

            assertThatThrownBy(() -> transactionService.deleteOwnPending(1L, "john.doe"))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("APPROVED");
            verify(transactionRepository, never()).delete(any());
        }

        @Test @DisplayName("throws InvalidStateTransitionException when transaction is REJECTED")
        void delete_rejectedTx_throwsInvalidStateTransition() {
            pendingTx.setStatus(TransactionStatus.REJECTED);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));

            assertThatThrownBy(() -> transactionService.deleteOwnPending(1L, "john.doe"))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("REJECTED");
            verify(transactionRepository, never()).delete(any());
        }

        @Test @DisplayName("throws ResourceNotFoundException when transaction not found")
        void delete_notFound_throwsResourceNotFound() {
            when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.deleteOwnPending(99L, "john.doe"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction").hasMessageContaining("99");
        }

        @Test @DisplayName("throws ResourceNotFoundException when acting user not found")
        void delete_userNotFound_throwsResourceNotFound() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pendingTx));
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.deleteOwnPending(1L, "ghost"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User").hasMessageContaining("ghost");
        }
    }

    // ── updateNotes() ─────────────────────────────────────────────────

    @Nested @DisplayName("updateNotes()")
    class UpdateNotes {

        private Transaction existingTx;

        @BeforeEach
        void setup() {
            existingTx = Transaction.builder()
                    .id(1L).submittedBy(regularUser).medicine(medicine)
                    .quantity(5).status(TransactionStatus.PENDING)
                    .notes("Original notes here at dispatch").build();
            existingTx.setSubmittedAt(LocalDateTime.now());
        }

        @Test @DisplayName("updates notes and returns updated response")
        void updateNotes_valid_savesAndReturns() {
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setNotes("Updated notes for this record");

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(existingTx));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionMapper.toResponse(any())).thenAnswer(inv -> {
                Transaction t = inv.getArgument(0);
                TransactionResponse r = new TransactionResponse();
                r.setNotes(t.getNotes()); return r;
            });

            TransactionResponse res = transactionService.updateNotes(1L, req);

            assertThat(res.getNotes()).isEqualTo("Updated notes for this record");
            verify(transactionRepository).save(argThat(t -> "Updated notes for this record".equals(t.getNotes())));
        }

        @Test @DisplayName("throws ResourceNotFoundException when transaction not found")
        void updateNotes_notFound_throwsResourceNotFound() {
            when(transactionRepository.findById(99L)).thenReturn(Optional.empty());
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setNotes("Valid note here");

            assertThatThrownBy(() -> transactionService.updateNotes(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction").hasMessageContaining("99");
        }

        @Test @DisplayName("throws IllegalArgumentException when notes are blank")
        void updateNotes_blankNotes_throwsIllegalArgument() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(existingTx));
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setNotes("   ");

            assertThatThrownBy(() -> transactionService.updateNotes(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("adjustment note is required");
        }

        @Test @DisplayName("throws IllegalArgumentException when notes are too short")
        void updateNotes_tooShort_throwsIllegalArgument() {
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(existingTx));
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setNotes("Hi");

            assertThatThrownBy(() -> transactionService.updateNotes(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("5 and 500");
        }
    }
}
