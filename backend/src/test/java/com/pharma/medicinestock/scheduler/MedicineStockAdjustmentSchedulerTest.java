package com.pharma.medicinestock.scheduler;

import com.pharma.medicinestock.entity.MedicineStockAdjustment;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicineStockAdjustmentScheduler")
class MedicineStockAdjustmentSchedulerTest {

    @Mock MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;

    @InjectMocks MedicineStockAdjustmentScheduler scheduler;

    private MedicineStockAdjustment makeAdj(LocalDateTime adjustedAt, int transitDays) {
        MedicineStockAdjustment a = new MedicineStockAdjustment();
        a.setInTransit(true);
        a.setAdjustedAt(adjustedAt);
        a.setTransitDays(transitDays);
        return a;
    }

    @Test
    @DisplayName("completes without throwing when no in-transit adjustments exist")
    void doesNotThrowWhenNothingToExpire() {
        when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of());

        assertDoesNotThrow(() -> scheduler.expireInTransitAdjustments());
        verify(medicineStockAdjustmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("flips inTransit to false for adjustments whose transitDays have elapsed")
    void expiredAdjustmentsGetFlipped() {
        MedicineStockAdjustment expired = makeAdj(LocalDateTime.now().minusDays(3), 2);

        when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(expired));

        scheduler.expireInTransitAdjustments();

        verify(medicineStockAdjustmentRepository).saveAll(argThat(saved -> {
            var list = (List<?>) saved;
            return list.size() == 1 && !((MedicineStockAdjustment) list.get(0)).isInTransit();
        }));
    }

    @Test
    @DisplayName("does not flip adjustments still within their transitDays window")
    void activeAdjustmentsNotFlipped() {
        MedicineStockAdjustment active = makeAdj(LocalDateTime.now().minusHours(12), 2);

        when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(active));

        scheduler.expireInTransitAdjustments();

        verify(medicineStockAdjustmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("respects per-record transitDays: 5-day window not expired after 3 days")
    void respectsPerRecordTransitDays() {
        MedicineStockAdjustment adj = makeAdj(LocalDateTime.now().minusDays(3), 5);

        when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(adj));

        scheduler.expireInTransitAdjustments();

        verify(medicineStockAdjustmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("only expires records past their own transitDays when mixed set")
    void mixedSetOnlyExpiresOldOnes() {
        MedicineStockAdjustment expired = makeAdj(LocalDateTime.now().minusDays(3), 2);
        MedicineStockAdjustment active  = makeAdj(LocalDateTime.now().minusHours(6), 2);

        when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(expired, active));

        scheduler.expireInTransitAdjustments();

        verify(medicineStockAdjustmentRepository).saveAll(argThat(saved -> {
            var list = (List<?>) saved;
            return list.size() == 1 && !((MedicineStockAdjustment) list.get(0)).isInTransit();
        }));
    }
}
