package org.kucherenkoos.carsharingservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.stripe.model.checkout.Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kucherenkoos.carsharingservice.dto.payment.PaymentRequestDto;
import org.kucherenkoos.carsharingservice.dto.payment.PaymentResponseDto;
import org.kucherenkoos.carsharingservice.event.payment.PaymentSuccessfulEvent;
import org.kucherenkoos.carsharingservice.mapper.PaymentMapper;
import org.kucherenkoos.carsharingservice.model.Car;
import org.kucherenkoos.carsharingservice.model.Payment;
import org.kucherenkoos.carsharingservice.model.Rental;
import org.kucherenkoos.carsharingservice.model.Role;
import org.kucherenkoos.carsharingservice.model.RoleName;
import org.kucherenkoos.carsharingservice.model.User;
import org.kucherenkoos.carsharingservice.repository.PaymentRepository;
import org.kucherenkoos.carsharingservice.repository.RentalRepository;
import org.kucherenkoos.carsharingservice.service.UserService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RentalRepository rentalRepository;
    @Mock
    private StripeService stripeService;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserService userService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User user;
    private Car car;
    private Rental rental;
    private Payment payment;
    private Session stripeSession;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");

        car = new Car();
        car.setId(1L);
        car.setDailyFee(BigDecimal.valueOf(100));

        rental = new Rental();
        rental.setId(1L);
        rental.setUser(user);
        rental.setCar(car);
        rental.setRentalDate(LocalDate.now().minusDays(2));
        rental.setReturnDate(LocalDate.now());

        payment = new Payment();
        payment.setId(1L);
        payment.setRental(rental);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setTotal(BigDecimal.valueOf(300));
        payment.setSessionId("stripe_session_123");

        stripeSession = mock(Session.class);
    }


    @Test
    @DisplayName("Create payment: Success for returned car (PAYMENT type)")
    void createPaymentSession_ValidRental_ReturnsPaymentResponseDto() {
        // Given
        rental.setActualReturnDate(LocalDate.now());
        PaymentRequestDto requestDto = new PaymentRequestDto();
        requestDto.setRentalId(rental.getId());

        when(rentalRepository.findById(requestDto.getRentalId())).thenReturn(Optional.of(rental));
        when(userService.getCurrentUser()).thenReturn(user);
        when(paymentRepository.findByRentalIdAndStatus(rental.getId(), Payment.PaymentStatus.PENDING))
                .thenReturn(Optional.empty());

        when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/...");
        when(stripeSession.getId()).thenReturn("stripe_session_123");
        when(stripeService.createStripeSession(any(), any(), any())).thenReturn(stripeSession);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(new PaymentResponseDto());

        // When
        PaymentResponseDto result = paymentService.createPaymentSession(requestDto);

        // Then
        assertNotNull(result);
        verify(stripeService, times(1)).createStripeSession(any(), any(), any());
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Create payment: Throws Exception if Car is NOT returned yet")
    void createPaymentSession_CarNotReturned_ThrowsIllegalStateException() {
        // Given
        rental.setActualReturnDate(null);
        PaymentRequestDto requestDto = new PaymentRequestDto();
        requestDto.setRentalId(rental.getId());

        when(rentalRepository.findById(requestDto.getRentalId())).thenReturn(Optional.of(rental));
        when(userService.getCurrentUser()).thenReturn(user);
        when(paymentRepository.findByRentalIdAndStatus(rental.getId(), Payment.PaymentStatus.PENDING))
                .thenReturn(Optional.empty());

        // When & Then
        Exception exception = assertThrows(IllegalStateException.class,
                () -> paymentService.createPaymentSession(requestDto));

        assertEquals("You should return car first. Rental id: " + rental.getId(), exception.getMessage());
        verify(stripeService, never()).createStripeSession(any(), any(), any());
    }

    @Test
    @DisplayName("Create payment: Access Denied if User is not Rental owner")
    void createPaymentSession_UserMismatch_ThrowsAccessDeniedException() {
        // Given
        PaymentRequestDto requestDto = new PaymentRequestDto();
        requestDto.setRentalId(rental.getId());

        User differentUser = new User();
        differentUser.setId(2L);

        when(rentalRepository.findById(requestDto.getRentalId())).thenReturn(Optional.of(rental));
        when(userService.getCurrentUser()).thenReturn(differentUser);

        // When & Then
        assertThrows(AccessDeniedException.class,
                () -> paymentService.createPaymentSession(requestDto));
    }

    @Test
    @DisplayName("Create payment: Returns existing PENDING payment if found")
    void createPaymentSession_PendingPaymentExists_ReturnsExistingPayment() {
        // Given
        PaymentRequestDto requestDto = new PaymentRequestDto();
        requestDto.setRentalId(rental.getId());

        when(rentalRepository.findById(requestDto.getRentalId())).thenReturn(Optional.of(rental));
        when(userService.getCurrentUser()).thenReturn(user);
        when(paymentRepository.findByRentalIdAndStatus(rental.getId(), Payment.PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(paymentMapper.toDto(payment)).thenReturn(new PaymentResponseDto());

        // When
        PaymentResponseDto result = paymentService.createPaymentSession(requestDto);

        // Then
        assertNotNull(result);
        verify(stripeService, never()).createStripeSession(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Process success payment: Updates status to PAID and publishes event")
    void processSuccessPayment_ValidSession_Success() {
        // Given
        String sessionId = "stripe_session_123";
        when(paymentRepository.findBySessionId(sessionId)).thenReturn(Optional.of(payment));
        when(stripeService.getSession(sessionId)).thenReturn(stripeSession);
        when(stripeSession.getPaymentStatus()).thenReturn("paid");

        // When
        paymentService.processSuccessPayment(sessionId);

        // Then
        assertEquals(Payment.PaymentStatus.PAID, payment.getStatus());
        verify(paymentRepository, times(1)).save(payment);
        verify(eventPublisher, times(1)).publishEvent(any(PaymentSuccessfulEvent.class));
    }

    @Test
    @DisplayName("Process success payment: Throws Exception if Stripe status is not 'paid'")
    void processSuccessPayment_StripeStatusNotPaid_ThrowsIllegalStateException() {
        // Given
        String sessionId = "stripe_session_123";
        when(paymentRepository.findBySessionId(sessionId)).thenReturn(Optional.of(payment));
        when(stripeService.getSession(sessionId)).thenReturn(stripeSession);
        when(stripeSession.getPaymentStatus()).thenReturn("unpaid");

        // When & Then
        assertThrows(IllegalStateException.class,
                () -> paymentService.processSuccessPayment(sessionId));
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Get payments: Manager requests all payments (userId is null)")
    void getPayments_AsManagerUserIdNull_ReturnsAllPayments() {
        // Given
        Role managerRole = new Role();
        managerRole.setName(RoleName.ROLE_MANAGER);
        user.setRoles(Set.of(managerRole));

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(new PaymentResponseDto());

        // When
        List<PaymentResponseDto> result = paymentService.getPayments(null, user);

        // Then
        assertEquals(1, result.size());
        verify(paymentRepository, times(1)).findAll();
        verify(paymentRepository, never()).findAllByRentalUserId(any());
    }

    @Test
    @DisplayName("Get payments: Regular User requests their payments")
    void getPayments_AsRegularUser_ReturnsUserPayments() {
        // Given
        Role userRole = new Role();
        userRole.setName(RoleName.ROLE_USER);
        user.setRoles(Set.of(userRole));

        when(paymentRepository.findAllByRentalUserId(user.getId())).thenReturn(List.of(payment));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(new PaymentResponseDto());

        // When
        List<PaymentResponseDto> result = paymentService.getPayments(999L, user);

        // Then
        assertEquals(1, result.size());
        verify(paymentRepository, times(1)).findAllByRentalUserId(user.getId());
    }

    @Test
    @DisplayName("Renew payment: Success for EXPIRED payment")
    void renewPaymentSession_ValidExpiredPayment_Success() {
        // Given
        payment.setStatus(Payment.PaymentStatus.EXPIRED);

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(userService.getCurrentUser()).thenReturn(user); // owner

        when(stripeSession.getId()).thenReturn("new_session_id");
        when(stripeSession.getUrl()).thenReturn("https://new.url");
        when(stripeService.createStripeSession(payment.getTotal(), payment.getType(), rental)).thenReturn(stripeSession);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(new PaymentResponseDto());

        // When
        paymentService.renewPaymentSession(payment.getId());

        // Then
        assertEquals(Payment.PaymentStatus.PENDING, payment.getStatus());
        assertEquals("new_session_id", payment.getSessionId());
        verify(stripeService, times(1)).createStripeSession(any(), any(), any());
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    @DisplayName("Renew payment: Throws Exception if payment is already PAID")
    void renewPaymentSession_WrongStatus_ThrowsIllegalStateException() {
        // Given
        payment.setStatus(Payment.PaymentStatus.PAID);

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        // When & Then
        assertThrows(IllegalStateException.class,
                () -> paymentService.renewPaymentSession(payment.getId()));
        verify(stripeService, never()).createStripeSession(any(), any(), any());
    }

    @Test
    @DisplayName("Calculate fine: Correctly applies FINE_MULTIPLIER for overdue rentals")
    void createPaymentSession_OverdueRental_CalculatesFineCorrectly() {
        // Given:
        BigDecimal dailyFee = BigDecimal.valueOf(50);
        car.setDailyFee(dailyFee);

        LocalDate startDate = LocalDate.now().minusDays(5);
        LocalDate plannedEndDate = LocalDate.now().minusDays(3);

        LocalDate actualReturnDate = LocalDate.now().minusDays(1);

        rental.setRentalDate(startDate);
        rental.setReturnDate(plannedEndDate);
        rental.setActualReturnDate(actualReturnDate);

        PaymentRequestDto requestDto = new PaymentRequestDto();
        requestDto.setRentalId(rental.getId());

        when(rentalRepository.findById(requestDto.getRentalId())).thenReturn(Optional.of(rental));
        when(userService.getCurrentUser()).thenReturn(user);
        when(paymentRepository.findByRentalIdAndStatus(rental.getId(), Payment.PaymentStatus.PENDING))
                .thenReturn(Optional.empty());

        when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/...");
        when(stripeSession.getId()).thenReturn("stripe_session_123");

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);

        when(stripeService.createStripeSession(amountCaptor.capture(), eq(Payment.PaymentType.FINE), any(Rental.class)))
                .thenReturn(stripeSession);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(new PaymentResponseDto());

        // When
        paymentService.createPaymentSession(requestDto);

        // Then
        BigDecimal capturedAmount = amountCaptor.getValue();

        BigDecimal expectedAmount = BigDecimal.valueOf(250.0);

        assertEquals(0, expectedAmount.compareTo(capturedAmount),
                "The calculated fine amount should exactly match the expected formula result");
    }

    // ==========================================
    // ТЕСТИ ДЛЯ МЕТОДУ calculateMoneyToPay
    // ==========================================

    @Test
    @DisplayName("Calculate payment: TYPE=PAYMENT, car returned earlier than planned")
    void calculateMoneyToPay_PaymentTypeEarlyReturn_CalculatesCorrectly() {
        // Given
        rental.setRentalDate(LocalDate.of(2026, 6, 1));
        rental.setReturnDate(LocalDate.of(2026, 6, 6));
        rental.setActualReturnDate(LocalDate.of(2026, 6, 3));

        car.setDailyFee(BigDecimal.valueOf(100));

        // When
        BigDecimal result = paymentService.calculateMoneyToPay(rental, Payment.PaymentType.PAYMENT);

        // Then
        assertEquals(0, BigDecimal.valueOf(300).compareTo(result), "Should charge only for actual days if returned early");
    }

    @Test
    @DisplayName("Calculate payment: TYPE=PAYMENT, car returned on time or later")
    void calculateMoneyToPay_PaymentTypeNormal_CalculatesCorrectly() {
        // Given
        rental.setRentalDate(LocalDate.of(2026, 6, 1));
        rental.setReturnDate(LocalDate.of(2026, 6, 4));
        rental.setActualReturnDate(LocalDate.of(2026, 6, 6));

        car.setDailyFee(BigDecimal.valueOf(100));

        // When
        BigDecimal result = paymentService.calculateMoneyToPay(rental, Payment.PaymentType.PAYMENT);

        // Then
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result), "Should charge only for planned days for ordinary PAYMENT");
    }

    @Test
    @DisplayName("Calculate fine: TYPE=FINE, actual return date is null throws Exception")
    void calculateMoneyToPay_FineTypeActualReturnNull_ThrowsIllegalStateException() {
        // Given
        rental.setActualReturnDate(null);

        // When & Then
        Exception exception = assertThrows(IllegalStateException.class, () ->
                paymentService.calculateMoneyToPay(rental, Payment.PaymentType.FINE)
        );

        assertEquals("Cannot calculate fine: actual return date is null", exception.getMessage());
    }

    @Test
    @DisplayName("Calculate fine: TYPE=FINE, returned on time (no overdue)")
    void calculateMoneyToPay_FineTypeNoOverdue_ReturnsZero() {
        // Given
        rental.setRentalDate(LocalDate.of(2026, 6, 1));
        rental.setReturnDate(LocalDate.of(2026, 6, 5));
        rental.setActualReturnDate(LocalDate.of(2026, 6, 5)); // Вчасно

        // When
        BigDecimal result = paymentService.calculateMoneyToPay(rental, Payment.PaymentType.FINE);

        // Then
        assertEquals(0, BigDecimal.ZERO.compareTo(result), "Fine should be zero if there is no overdue");
    }

    @Test
    @DisplayName("Calculate fine: TYPE=FINE, calculation with overdue and multiplier")
    void calculateMoneyToPay_FineTypeWithOverdue_CalculatesCorrectly() {
        // Given

        rental.setRentalDate(LocalDate.of(2026, 6, 1));
        rental.setReturnDate(LocalDate.of(2026, 6, 5));
        rental.setActualReturnDate(LocalDate.of(2026, 6, 7));

        car.setDailyFee(BigDecimal.valueOf(100));

        BigDecimal fineMultiplier = BigDecimal.valueOf(1.5);
        BigDecimal expected = car.getDailyFee().multiply(
                BigDecimal.valueOf(4).add(BigDecimal.valueOf(2).multiply(fineMultiplier))
        );

        // When
        BigDecimal result = paymentService.calculateMoneyToPay(rental, Payment.PaymentType.FINE);

        // Then
        assertEquals(0, expected.compareTo(result), "Fine calculation formula should match business rules");
    }

    @Test
    @DisplayName("Calculate payment: Unknown payment type throws Exception")
    void calculateMoneyToPay_UnknownType_ThrowsIllegalArgumentException() {
        // Given
        rental.setActualReturnDate(LocalDate.now());

        // When & Then
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                paymentService.calculateMoneyToPay(rental, null)
        );

        assertEquals("Unknown payment type: null", exception.getMessage());
    }
}
