package org.kucherenkoos.carsharingservice.service.impl;

import com.stripe.model.checkout.Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kucherenkoos.carsharingservice.dto.payment.PaymentRequestDto;
import org.kucherenkoos.carsharingservice.dto.payment.PaymentResponseDto;
import org.kucherenkoos.carsharingservice.event.payment.PaymentSuccessfulEvent;
import org.kucherenkoos.carsharingservice.exception.EntityNotFoundException;
import org.kucherenkoos.carsharingservice.mapper.PaymentMapper;
import org.kucherenkoos.carsharingservice.model.Payment;
import org.kucherenkoos.carsharingservice.model.Rental;
import org.kucherenkoos.carsharingservice.model.User;
import org.kucherenkoos.carsharingservice.repository.PaymentRepository;
import org.kucherenkoos.carsharingservice.repository.RentalRepository;
import org.kucherenkoos.carsharingservice.service.PaymentService;
import org.kucherenkoos.carsharingservice.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private static final BigDecimal FINE_MULTIPLIER = new BigDecimal("1.5");
    private static final Logger LOGGER = LogManager.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final RentalRepository rentalRepository;
    private final StripeService stripeService;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;

    @Override
    @Transactional
    public PaymentResponseDto createPaymentSession(PaymentRequestDto requestDto) {
        Rental rental = rentalRepository.findById(requestDto.getRentalId())
                .orElseThrow(() -> {
                    LOGGER.error("Rental not found by id {}", requestDto.getRentalId());
                    return new EntityNotFoundException("Rental not found by id: "
                            + requestDto.getRentalId());
                });

        User currentUser = userService.getCurrentUser();
        if (!rental.getUser().getId().equals(currentUser.getId())) {
            LOGGER.warn("Access denied. User {} tried to create a payment for rental {}",
                     currentUser.getId(), rental.getId());
            throw new AccessDeniedException("You don't have permission "
                    + "to create a payment for this rental.");
        }

        Optional<Payment> existingPayment =
                paymentRepository.findByRentalIdAndStatus(
                        rental.getId(),
                        Payment.PaymentStatus.PENDING
                );

        if (existingPayment.isPresent()) {
            return paymentMapper.toDto(existingPayment.get());
        }

        Payment.PaymentType type = resolvePaymentType(rental);
        BigDecimal moneyToPay = calculateMoneyToPay(rental, type);

        Session session = stripeService.createStripeSession(moneyToPay, type, rental);

        Payment payment = new Payment();
        payment.setRental(rental);
        payment.setSessionUrl(session.getUrl());
        payment.setSessionId(session.getId());
        payment.setTotal(moneyToPay);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setType(type);

        Payment savedPayment = paymentRepository.save(payment);

        return paymentMapper.toDto(savedPayment);
    }

    @Override
    public List<PaymentResponseDto> getPayments(Long requestedUserId, User currentUser) {
        boolean isManager = currentUser.getRoles().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_MANAGER"));

        List<Payment> payments;

        if (isManager) {
            if (requestedUserId == null) {
                payments = paymentRepository.findAll();
            } else {
                payments = paymentRepository.findAllByRentalUserId(requestedUserId);
            }
        } else {
            payments = paymentRepository.findAllByRentalUserId(currentUser.getId());
        }

        return payments.stream()
                .map(paymentMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void processSuccessPayment(String sessionId) {
        Payment payment = paymentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> {
                    LOGGER.error("processSuccessPayment: payment not found by sessionId {}",
                            sessionId);
                    return new EntityNotFoundException("Payment session not found: " + sessionId);
                });

        Session session = stripeService.getSession(sessionId);

        if (!"paid".equals(session.getPaymentStatus())) {
            LOGGER.error("Payment not successful for session with status: {}",
                    session.getPaymentStatus());
            throw new IllegalStateException("Payment was not successful. Stripe status: "
                    + session.getPaymentStatus());
        }

        payment.setStatus(Payment.PaymentStatus.PAID);
        paymentRepository.save(payment);
        eventPublisher.publishEvent(new PaymentSuccessfulEvent(payment));
    }

    @Override
    @Transactional
    public void processCancelPayment(String sessionId) {
        Payment payment = paymentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> {
                    LOGGER.error("processCancelPayment: payment not found by sessionId {}",
                            sessionId);
                    return new EntityNotFoundException("Payment session not found: " + sessionId);
                });

        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            return;
        }

        Session session = stripeService.getSession(sessionId);

        if ("paid".equals(session.getPaymentStatus())) {
            payment.setStatus(Payment.PaymentStatus.PAID);
            eventPublisher.publishEvent(new PaymentSuccessfulEvent(payment));
        } else {
            payment.setStatus(Payment.PaymentStatus.CANCELED);
        }

        paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto renewPaymentSession(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    LOGGER.error("renewPaymentSession: payment not found by id {}",
                            paymentId);
                    return new EntityNotFoundException("Payment not found by id: " + paymentId);
                });

        if (payment.getStatus() != Payment.PaymentStatus.EXPIRED
                && payment.getStatus() != Payment.PaymentStatus.CANCELED) {
            LOGGER.warn("Attempt to renew payment {} with status {}",
                    paymentId,
                    payment.getStatus());
            throw new IllegalStateException("Only expired or canceled payments can be renewed");
        }

        User currentUser = userService.getCurrentUser();
        if (!payment.getRental().getUser().getId().equals(currentUser.getId())) {
            LOGGER.warn("Access denied. User {} tried to renew payment {}",
                    currentUser.getId(), payment.getId());
            throw new AccessDeniedException("You don't have permission to renew this payment.");
        }

        Session session = stripeService.createStripeSession(
                payment.getTotal(),
                payment.getType(),
                payment.getRental()
        );

        payment.setSessionId(session.getId());
        payment.setSessionUrl(session.getUrl());
        payment.setStatus(Payment.PaymentStatus.PENDING);

        return paymentMapper.toDto(paymentRepository.save(payment));
    }

    private Payment.PaymentType resolvePaymentType(Rental rental) {
        if (rental.getActualReturnDate() == null) {
            LOGGER.warn("Not returned rental, type method stop. Rental id: {}",
                    rental.getId());
            throw new IllegalStateException("You should return car first. Rental id: "
                    + rental.getId());
        }

        if (rental.getActualReturnDate().isAfter(rental.getReturnDate())) {
            return Payment.PaymentType.FINE;
        }

        return Payment.PaymentType.PAYMENT;
    }

    protected BigDecimal calculateMoneyToPay(Rental rental, Payment.PaymentType type) {
        BigDecimal dailyFee = rental.getCar().getDailyFee();

        LocalDate startPlan = rental.getRentalDate();
        LocalDate endPlan = rental.getReturnDate();
        LocalDate actualEndPlan = rental.getActualReturnDate();

        if (type == Payment.PaymentType.PAYMENT) {

            if (actualEndPlan.isBefore(endPlan)) {
                long days = ChronoUnit.DAYS.between(startPlan, actualEndPlan) + 1;
                return dailyFee.multiply(BigDecimal.valueOf(days));
            }

            long days = ChronoUnit.DAYS.between(startPlan, endPlan) + 1;
            return dailyFee.multiply(BigDecimal.valueOf(days));
        }

        if (type == Payment.PaymentType.FINE) {

            if (rental.getActualReturnDate() == null) {
                LOGGER.error("Actual return date is null. Rental id: {}", rental.getId());
                throw new IllegalStateException(
                        "Cannot calculate fine: actual return date is null"
                );
            }

            long plannedDays = ChronoUnit.DAYS.between(startPlan, endPlan);
            long overdueDays = ChronoUnit.DAYS.between(endPlan, actualEndPlan);

            if (overdueDays <= 0) {
                return BigDecimal.ZERO;
            }

            return dailyFee
                    .multiply(BigDecimal.valueOf(plannedDays)
                            .add(BigDecimal.valueOf(overdueDays)
                                    .multiply(FINE_MULTIPLIER)
                    )
            );
        }
        LOGGER.error("Payment type not found. Type {}", type);
        throw new IllegalArgumentException("Unknown payment type: " + type);
    }
}
