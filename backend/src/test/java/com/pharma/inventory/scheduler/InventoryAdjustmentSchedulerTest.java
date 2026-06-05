package com.pharma.inventory.scheduler;

import com.pharma.inventory.entity.InventoryAdjustment;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAdjustmentScheduler")
class InventoryAdjustmentSchedulerTest {

    @Mock InventoryAdjustmentRepository inventoryAdjustmentRepository;

    @InjectMocks InventoryAdjustmentScheduler scheduler;

    private InventoryAdjustment makeAdj(LocalDateTime adjustedAt, int transitDays) {
        InventoryAdjustment a = new InventoryAdjustment();
        a.setInTransit(true);
        a.setAdjustedAt(adjustedAt);
        a.setTransitDays(transitDays);
        return a;
    }

    @Test
    @DisplayName("completes without throwing when no in-transit adjustments exist")
    void doesNotThrowWhenNothingToExpire() {
        when(inventoryAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of());

        assertDoesNotThrow(() -> scheduler.expireInTransitAdjustments());
        verify(inventoryAdjustmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("flips inTransit to false for adjustments whose transitDays have elapsed")
    void expiredAdjustmentsGetFlipped() {
        InventoryAdjustment expired = makeAdj(LocalDateTime.now().minusDays(3), 2);

        when(inventoryAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(expired));

        scheduler.expireInTransitAdjustments();

        verify(inventoryAdjustmentRepository).saveAll(argThat(saved -> {
            var list = (List<?>) saved;
            return list.size() == 1 && !((InventoryAdjustment) list.get(0)).isInTransit();
        }));
    }

    @Test
    @DisplayName("does not flip adjustments still within their transitDays window")
    void activeAdjustmentsNotFlipped() {
        InventoryAdjustment active = makeAdj(LocalDateTime.now().minusHours(12), 2);

        when(inventoryAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(active));

        scheduler.expireInTransitAdjustments();

        verify(inventoryAdjustmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("respects per-record transitDays: 5-day window not expired after 3 days")
    void respectsPerRecordTransitDays() {
        InventoryAdjustment adj = makeAdj(LocalDateTime.now().minusDays(3), 5);

        when(inventoryAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(adj));

        scheduler.expireInTransitAdjustments();

        verify(inventoryAdjustmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("only expires records past their own transitDays when mixed set")
    void mixedSetOnlyExpiresOldOnes() {
        InventoryAdjustment expired = makeAdj(LocalDateTime.now().minusDays(3), 2);
        InventoryAdjustment active  = makeAdj(LocalDateTime.now().minusHours(6), 2);

        when(inventoryAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(expired, active));

        scheduler.expireInTransitAdjustments();

        verify(inventoryAdjustmentRepository).saveAll(argThat(saved -> {
            var list = (List<?>) saved;
            return list.size() == 1 && !((InventoryAdjustment) list.get(0)).isInTransit();
        }));
    }
}
