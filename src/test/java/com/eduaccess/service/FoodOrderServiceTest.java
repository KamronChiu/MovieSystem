package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.BookingSeatRepository;
import com.eduaccess.repository.FoodItemRepository;
import com.eduaccess.repository.FoodOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UT_026 — Unit tests for {@link FoodOrderService} status guard.
 */
@ExtendWith(MockitoExtension.class)
class FoodOrderServiceTest {

    @Mock private FoodItemRepository foodItemRepository;
    @Mock private FoodOrderRepository foodOrderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private FoodOrderService foodOrderService;

    @Test
    @DisplayName("UT_026 updateStatus_invalidTransition_rejected")
    void updateStatus_invalidTransition_rejected() {
        // A DELIVERED food order must NOT be cancellable or marked preparing again.
        Booking mockBooking = mock(Booking.class);
        FoodOrder delivered = new FoodOrder(mockBooking, DeliveryMethod.COUNTER_PICKUP);
        delivered.setStatus(FoodOrderStatus.DELIVERED);

        when(foodOrderRepository.findById(1L)).thenReturn(Optional.of(delivered));

        // DELIVERED → cancelOrder should throw
        assertThatThrownBy(() -> foodOrderService.cancelOrder(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delivered food orders cannot be cancelled");

        // DELIVERED → markPreparing should also throw
        assertThatThrownBy(() -> foodOrderService.markPreparing(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delivered food orders cannot be changed back to preparing");
    }
}
