package com.keni.starter.modules.payments;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.payments.dtos.PaymentResponse;
import com.keni.starter.modules.payments.dtos.UpdatePaymentStatusRequest;
import com.keni.starter.modules.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/payment")
public class PaymentController {
  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  /**
   * No POST here on purpose. Payments come from POST /user-subscription, which creates
   * one alongside the subscription in the same transaction.
   */

  @PatchMapping("/{id}/status")
  public PaymentResponse updateStatus(@PathVariable UUID id,
      @Valid @RequestBody UpdatePaymentStatusRequest request) {
    return paymentService.updateStatus(id, request);
  }

  @GetMapping("/me")
  public PageResponse<PaymentResponse> getMyPayments(@AuthenticationPrincipal User currentUser,
      @PageableDefault(size = 20, sort = "datePaid", direction = Sort.Direction.DESC)
      Pageable pageable) {
    return paymentService.getPaymentsByUserId(currentUser.getId(), pageable);
  }

  @GetMapping
  public PageResponse<PaymentResponse> getAllPayments(
      @PageableDefault(size = 20, sort = "datePaid", direction = Sort.Direction.DESC)
      Pageable pageable) {
    return paymentService.getAllPayments(pageable);
  }
}