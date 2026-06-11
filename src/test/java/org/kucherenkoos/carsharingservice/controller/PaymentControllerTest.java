package org.kucherenkoos.carsharingservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.checkout.Session;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kucherenkoos.carsharingservice.dto.payment.PaymentRequestDto;
import org.kucherenkoos.carsharingservice.model.User;
import org.kucherenkoos.carsharingservice.service.impl.StripeService;
import org.kucherenkoos.carsharingservice.service.impl.TelegramNotificationService;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(
        scripts = "classpath:database/payments/add-payment-data.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        scripts = "classpath:database/payments/clear-payment-data.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StripeService stripeService;

    @MockitoBean
    private TelegramNotificationService telegramNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private Authentication userAuth;
    private Authentication managerAuth;

    @BeforeEach
    void setUp() {
        User dbUser = new User();
        dbUser.setId(100L);
        dbUser.setEmail("user@example.com");

        userAuth = new UsernamePasswordAuthenticationToken(
                dbUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        User dbManager = new User();
        dbManager.setId(100L);
        dbManager.setEmail("manager@example.com");

        managerAuth = new UsernamePasswordAuthenticationToken(
                dbManager, null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
    }

    @Test
    @DisplayName("Create payment session - Success")
    void createPaymentSession_ValidRequest_ReturnsResponseDto() throws Exception {
        PaymentRequestDto requestDto = new PaymentRequestDto();
        requestDto.setRentalId(100L);

        Session mockSession = Mockito.mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test_session");

        when(stripeService.createStripeSession(any(), any(), any())).thenReturn(mockSession);

        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(post("/payments")
                        .with(authentication(userAuth))
                        .content(jsonRequest)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sessionUrl").exists());
    }

    @Test
    @DisplayName("Get payments as USER - Returns list of payments")
    void getPayments_AsUser_ReturnsList() throws Exception {
        mockMvc.perform(get("/payments")
                        .with(authentication(userAuth))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(10L));
    }

    @Test
    @DisplayName("Get payments as MANAGER with specific user_id - Returns list")
    void getPayments_AsManagerWithUserId_ReturnsList() throws Exception {
        Long targetUserId = 100L;

        mockMvc.perform(get("/payments")
                        .with(authentication(managerAuth))
                        .param("user_id", targetUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(10L));
    }

    @Test
    @DisplayName("Handle success payment (Stripe callback) - Returns success message")
    void handleSuccessPayment_ValidSessionId_ReturnsMessage() throws Exception {
        String sessionId = "cs_test_123";

        Session mockSession = Mockito.mock(Session.class);
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getPaymentStatus()).thenReturn("paid");

        when(stripeService.getSession(sessionId)).thenReturn(mockSession);

        mockMvc.perform(get("/payments/success")
                        .param("session_id", sessionId))
                .andExpect(status().isOk())
                .andExpect(content().string("Payment successful!"));
    }

    @Test
    @DisplayName("Handle cancel payment (Stripe callback) - Returns cancel message")
    void handleCancelPayment_ValidSessionId_ReturnsMessage() throws Exception {
        String sessionId = "cs_test_123";
        String expectedMessage = "Payment paused. You can complete the transaction within 24 hours via the session link.";

        Session mockSession = Mockito.mock(Session.class);
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getPaymentStatus()).thenReturn("unpaid");

        when(stripeService.getSession(sessionId)).thenReturn(mockSession);

        mockMvc.perform(get("/payments/cancel")
                        .param("session_id", sessionId))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedMessage));
    }

    @Test
    @DisplayName("Renew expired payment session - Success")
    void renewPayment_ValidId_ReturnsResponseDto() throws Exception {
        Long paymentId = 10L;

        Session mockSession = Mockito.mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_renew_123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test_session_renew");

        when(stripeService.createStripeSession(any(), any(), any())).thenReturn(mockSession);

        mockMvc.perform(post("/payments/{id}/renew", paymentId)
                        .with(authentication(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.sessionUrl").exists());
    }
}
