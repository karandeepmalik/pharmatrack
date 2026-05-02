package com.pharma.inventory.mapper;

import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.entity.Transaction.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransactionMapper")
class TransactionMapperTest {

    private TransactionMapper mapper;
    private PharmaCompany pharma;
    private Medicine medicine;
    private User submitter;
    private User admin;

    @BeforeEach
    void setUp() {
        mapper = new TransactionMapper();

        pharma = new PharmaCompany();
        pharma.setId(10L); pharma.setName("FIP Shield");

        medicine = new Medicine();
        medicine.setId(1L); medicine.setName("FIP Vial");
        medicine.setType(Medicine.MedicineType.VIAL);
        medicine.setSpecification(10.0); medicine.setPharmaCompany(pharma);

        submitter = new User();
        submitter.setId(1L); submitter.setUsername("john.doe");
        submitter.setFullName("John Doe");

        admin = new User();
        admin.setId(2L); admin.setUsername("admin");
        admin.setFullName("Admin User");
    }

    private Transaction buildTx(TransactionStatus status) {
        Transaction t = Transaction.builder()
                .id(42L).submittedBy(submitter).medicine(medicine)
                .quantity(5).status(status).notes("Ward B dispatch today")
                .build();
        t.setSubmittedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        return t;
    }

    @Test @DisplayName("maps all scalar fields correctly")
    void toResponse_mapsAllScalarFields() {
        Transaction t = buildTx(TransactionStatus.PENDING);
        TransactionResponse r = mapper.toResponse(t);

        assertThat(r.getId()).isEqualTo(42L);
        assertThat(r.getQuantity()).isEqualTo(5);
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getNotes()).isEqualTo("Ward B dispatch today");
        assertThat(r.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0));
    }

    @Test @DisplayName("maps submitter user fields")
    void toResponse_mapsSubmitterFields() {
        TransactionResponse r = mapper.toResponse(buildTx(TransactionStatus.PENDING));
        assertThat(r.getSubmittedById()).isEqualTo(1L);
        assertThat(r.getSubmittedByUsername()).isEqualTo("john.doe");
        assertThat(r.getSubmittedByFullName()).isEqualTo("John Doe");
    }

    @Test @DisplayName("maps medicine fields including pharma")
    void toResponse_mapsMedicineAndPharmaFields() {
        TransactionResponse r = mapper.toResponse(buildTx(TransactionStatus.PENDING));
        assertThat(r.getMedicineId()).isEqualTo(1L);
        assertThat(r.getMedicineName()).isEqualTo("FIP Vial");
        assertThat(r.getMedicineType()).isEqualTo("VIAL");
        assertThat(r.getSpecification()).isEqualTo(10.0);
        assertThat(r.getPharmaId()).isEqualTo(10L);
        assertThat(r.getPharmaName()).isEqualTo("FIP Shield");
    }

    @Test @DisplayName("approver fields are null when transaction is PENDING")
    void toResponse_pendingTx_approverFieldsNull() {
        TransactionResponse r = mapper.toResponse(buildTx(TransactionStatus.PENDING));
        assertThat(r.getApprovedByUsername()).isNull();
        assertThat(r.getApprovedAt()).isNull();
    }

    @Test @DisplayName("maps approver fields when transaction is APPROVED")
    void toResponse_approvedTx_mapsApproverFields() {
        Transaction t = buildTx(TransactionStatus.APPROVED);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 4, 1, 11, 0);
        t.setApprovedBy(admin);
        t.setApprovedAt(approvedAt);

        TransactionResponse r = mapper.toResponse(t);
        assertThat(r.getApprovedByUsername()).isEqualTo("admin");
        assertThat(r.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test @DisplayName("maps null paymentScreenshot when none uploaded")
    void toResponse_noScreenshot_nullFields() {
        TransactionResponse r = mapper.toResponse(buildTx(TransactionStatus.PENDING));
        assertThat(r.getPaymentScreenshot()).isNull();
        assertThat(r.getPaymentScreenshotType()).isNull();
    }

    @Test @DisplayName("maps payment screenshot Base64 and MIME type")
    void toResponse_withScreenshot_mapsScreenshotFields() {
        String b64 = Base64.getEncoder().encodeToString("img-data".getBytes());
        Transaction t = buildTx(TransactionStatus.PENDING);
        t.setPaymentScreenshot(b64);
        t.setPaymentScreenshotType("image/jpeg");

        TransactionResponse r = mapper.toResponse(t);
        assertThat(r.getPaymentScreenshot()).isEqualTo(b64);
        assertThat(r.getPaymentScreenshotType()).isEqualTo("image/jpeg");
    }

    @Test @DisplayName("maps REJECTED status correctly")
    void toResponse_rejectedTx_mapsStatus() {
        TransactionResponse r = mapper.toResponse(buildTx(TransactionStatus.REJECTED));
        assertThat(r.getStatus()).isEqualTo("REJECTED");
    }
}
