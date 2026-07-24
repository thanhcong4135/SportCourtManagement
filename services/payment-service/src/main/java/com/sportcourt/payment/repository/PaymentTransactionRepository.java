package com.sportcourt.payment.repository;

import com.sportcourt.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByPaymentRef(String paymentRef);

    Optional<PaymentTransaction> findFirstByBookingIdAndCustomerEmailIsNotNullOrderByRequestedAtDesc(UUID bookingId);

    List<PaymentTransaction> findByBookingIdOrderByRequestedAtDesc(UUID bookingId);
}
