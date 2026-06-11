package org.kucherenkoos.carsharingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.kucherenkoos.carsharingservice.dto.payment.PaymentRequestDto;
import org.kucherenkoos.carsharingservice.dto.payment.PaymentResponseDto;
import org.kucherenkoos.carsharingservice.model.User;
import org.kucherenkoos.carsharingservice.service.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payment management", description = "Endpoints for managing payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Creating payment session")
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public PaymentResponseDto createPaymentSession(@RequestBody PaymentRequestDto requestDto) {
        return paymentService.createPaymentSession(requestDto);
    }

    @Operation(summary = "Get user payments (Manager may choose specific user")
    @PreAuthorize("hasRole('USER') or hasRole('MANAGER')")
    @GetMapping
    public List<PaymentResponseDto> getPayments(
            @RequestParam(value = "user_id", required = false) Long userId,
            @AuthenticationPrincipal User currentUser) {

        return paymentService.getPayments(userId, currentUser);
    }

    @Operation(summary = "Proceed success payment")
    @GetMapping("/success")
    public String handleSuccessPayment(@RequestParam("session_id") String sessionId) {
        paymentService.processSuccessPayment(sessionId);
        return "Payment successful!";
    }

    @Operation(summary = "Cancel payment session")
    @GetMapping("/cancel")
    public String handleCancelPayment(@RequestParam("session_id") String sessionId) {
        paymentService.processCancelPayment(sessionId);
        return "Payment paused. "
            + "You can complete the transaction within 24 hours via the session link.";
    }

    @Operation(summary = "Renew expired payment session")
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/renew")
    public PaymentResponseDto renewPayment(@PathVariable Long id) {
        return paymentService.renewPaymentSession(id);
    }
}
