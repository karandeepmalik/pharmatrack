package com.pharma.inventory.scheduler;

import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAdjustmentScheduler")
class InventoryAdjustmentSchedulerTest {

    @Mock InventoryAdjustmentRepository inventoryAdjustmentRepository;

    @InjectMocks InventoryAdjustmentScheduler scheduler;

    @Test
    @DisplayName("calls repository with cutoff approximately 2 days ago")
    void callsRepositoryWithCutoff() {
        LocalDateTime before = LocalDateTime.now().minusDays(2).minusSeconds(5);
        LocalDateTime after  = LocalDateTime.now().minusDays(2).plusSeconds(5);

        scheduler.expireInTransitAdjustments();

        verify(inventoryAdjustmentRepository).expireOldInTransitAdjustments(argThat(cutoff ->
                !cutoff.isBefore(before) && !cutoff.isAfter(after)
        ));
    }

    @Test
    @DisplayName("completes without throwing when no in-transit adjustments exist")
    void doesNotThrowWhenNothingToExpire() {
        when(inventoryAdjustmentRepository.expireOldInTransitAdjustments(any())).thenReturn(0);

        assertDoesNotThrow(() -> scheduler.expireInTransitAdjustments());
    }

    @Test
    @DisplayName("invokes repository exactly once per call")
    void calledOncePerInvocation() {
        scheduler.expireInTransitAdjustments();

        verify(inventoryAdjustmentRepository, times(1)).expireOldInTransitAdjustments(any());
    }

    @Test
    @DisplayName("does not call any other repository method")
    void onlyCallsExpireMethod() {
        scheduler.expireInTransitAdjustments();

        verify(inventoryAdjustmentRepository).expireOldInTransitAdjustments(any());
        verifyNoMoreInteractions(inventoryAdjustmentRepository);
    }
}
