package com.keni.starter.modules.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  // Payment holds @ManyToOne relations, so Spring Data resolves userId to user.id
  // and subscriptionId to subscription.id
  List<Payment> findByUserId(UUID userId);

  List<Payment> findBySubscriptionId(UUID subscriptionId);

  List<Payment> findByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);

  List<Payment> findByDatePaidBetween(Instant from, Instant to);
}