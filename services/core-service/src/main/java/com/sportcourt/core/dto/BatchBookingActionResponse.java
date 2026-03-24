package com.sportcourt.core.dto;

import java.math.BigDecimal;
import java.util.List;

public record BatchBookingActionResponse(
    List<BookingResponse> bookings,
    BigDecimal totalPrice,
    BigDecimal totalDepositRequired,
    BigDecimal totalDepositPaid
) {
}
