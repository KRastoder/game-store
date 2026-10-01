package com.keni.starter.modules.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  // Payment holds @ManyToOne relations, so Spring Data resolves userId to user.id
  // and subscriptionId to subscription.id
  Page<Payment> findByUserId(UUID userId, Pageable pageable);

  Page<Payment> findBySubscriptionId(UUID subscriptionId, Pageable pageable);

  Page<Payment> findByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId,
      Pageable pageable);

  Page<Payment> findByDatePaidBetween(Instant from, Instant to, Pageable pageable);
}