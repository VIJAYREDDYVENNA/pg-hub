package com.hostelmanagement.payments.online;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.payments.gateway.GatewayException;
import com.hostelmanagement.payments.gateway.MockPaymentGatewayService;
import com.hostelmanagement.payments.gateway.PaymentGatewayService;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.State;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.AttemptDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development helper that resolves a simulated PENDING payment, the way a bank confirming late would. It only does
 * anything while the mock gateway is active and always requires the staff permission to collect payments.
 */
@Tag(name = "Online payments")
@Validated
@RestController
@RequestMapping("/api/online-payments/mock")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
public class MockSettleController {

    private final PaymentGatewayService gateway;
    private final OnlinePaymentService service;

    @Operation(summary = "Simulated mode only: settle a pending simulated payment as SUCCEEDED or FAILED")
    @PostMapping("/attempts/{attemptId}/settle")
    public AttemptDto settle(@PathVariable Long attemptId,
                             @RequestParam @Pattern(regexp = "SUCCEEDED|FAILED") String outcome) {
        if (!(gateway instanceof MockPaymentGatewayService mock)) {
            throw ApiException.notFound("Resource");
        }
        PaymentAttempt attempt = service.attemptForStaff(attemptId);
        try {
            mock.settle(attempt.getProviderOrderId(), State.valueOf(outcome));
        } catch (GatewayException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        // The state change is only visible to the app through the normal reconciliation path
        return service.refresh(attempt);
    }
}
