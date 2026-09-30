package com.keni.starter.modules.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  List<Payment> findByUserId(UUID userId);

  List<Payment> findBySubId(UUID subId);

  List<Payment> findByUserIdAndSubId(UUID userId, UUID subId);

  List<Payment> findByDatePaidBetween(Instant from, Instant to);
}
