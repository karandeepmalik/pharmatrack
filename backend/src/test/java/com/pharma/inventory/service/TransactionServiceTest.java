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
    }

    // ── getAll() ──────────────────────────────────────────────────────

    @Nested @DisplayName("getAll()")
    class GetAll {

        @Test @DisplayName("returns empty list when no transactions exist")
        void getAll_noTransactions_returnsEmpty() {
            when(transactionRepository.findAllByOrderBySubmittedAtDesc())
                    .thenReturn(List.of());
            assertThat(transactionService.getAll()).isEmpty();
        }

        @Test @DisplayName("maps all transactions via mapper")
        void getAll_multipleTransactions_mapsAll() {
            Transaction t1 = savedTx(buildReq("Note one here for dispatch"));
            Transaction t2 = savedTx(buildReq("Note two for the clinic today"));
            t2.setId(2L);
            TransactionResponse r1 = stubResponse(t1);
            TransactionResponse r2 = stubResponse(t2);

            when(transactionRepository.findAllByOrderBySubmittedAtDesc())
                    .thenReturn(List.of(t1, t2));
            when(transactionMapper.toResponse(t1)).thenReturn(r1);
            when(transactionMapper.toResponse(t2)).thenReturn(r2);

            List<TransactionResponse> result = transactionService.getAll();
            assertThat(result).hasSize(2).containsExactly(r1, r2);
        }

        @Test @DisplayName("returns screenshots in response when present")
        void getAll_txWithScreenshots_includesScreenshotsInResponse() {
            String b64 = Base64.getEncoder().encodeToString("img".getBytes());
            Transaction tx = savedTx(buildReq("Dispatch with payment proof"));
            TransactionResponse resp = stubResponse(tx);
            resp.setScreenshots(List.of(new ScreenshotDto(b64, "image/png")));

            when(transactionRepository.findAllByOrderBySubmittedAtDesc())
                    .thenReturn(List.of(tx));
            when(transactionMapper.toResponse(tx)).thenReturn(resp);

            List<TransactionResponse> result = transactionService.getAll();
            assertThat(result.get(0).getScreenshots()).hasSize(1);
            assertThat(result.get(0).getScreenshots().get(0).getData()).isEqualTo(b64);
            assertThat(result.get(0).getScreenshots().get(0).getMimeType()).isEqualTo("image/png");
        }
    }

    // ── getByUser() ───────────────────────────────────────────────────

    @Nested @DisplayName("getByUser()")
    class GetByUser {

        @Test @DisplayName("returns empty list when user has no transactions")
        void getByUser_noTransactions_returnsEmpty() {
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(transactionRepository.findBySubmittedByOrderBySubmittedAtDesc(regularUser))
                    .thenReturn(List.of());
            assertThat(transactionService.getByUser("john.doe")).isEmpty();
        }

        @Test @DisplayName("throws ResourceNotFoundException when user not found")
        void getByUser_unknownUser_throwsResourceNotFound() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> transactionService.getByUser("nobody"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User").hasMessageContaining("nobody");
        }

        @Test @DisplayName("returns empty screenshots list when no screenshot uploaded")
        void getByUser_noScreenshot_emptyScreenshotsInResponse() {
            Transaction tx = savedTx(buildReq("Standard dispatch order five"));
            TransactionResponse resp = stubResponse(tx);

            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(regularUser));
            when(transactionRepository.findBySubmittedByOrderBySubmittedAtDesc(regularUser))
                    .thenReturn(List.of(tx));
            when(transactionMapper.toResponse(tx)).thenReturn(resp);

            assertThat(transactionService.getByUser("john.doe").get(0).getScreenshots()).isEmpty();
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
