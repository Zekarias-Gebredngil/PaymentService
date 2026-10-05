package com.payment.nadi.infrastructure.provider.telebirr;

import com.payment.nadi.infrastructure.configuration.PaymentProperties;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelebirrClientResilienceTest {

    private TelebirrClient telebirrClient;

    @BeforeEach
    void setUp() {
        WebClient webClient = mock(WebClient.class);
        PaymentProperties paymentProperties = new PaymentProperties();
        telebirrClient = new TelebirrClient(webClient, paymentProperties);
    }

    @Test
    @DisplayName("Should invoke createC2BOrderFallback when upstream gateway throws exception")
    void shouldReturnResilientFallbackForC2BOrder() {
        TelebirrC2BOrderRequest request = new TelebirrC2BOrderRequest(
                "app-1",
                "http://notify",
                "out-trade-100",
                "Nadi Payment",
                "http://return",
                "short-1",
                "TopUp Subject",
                "30m",
                "100.00",
                "nonce-1",
                "123456789",
                "sign-1"
        );

        TelebirrC2BOrderResponse fallbackResponse = telebirrClient.createC2BOrderFallback(
                request, new RuntimeException("Connection timed out to Telebirr")
        );

        assertThat(fallbackResponse).isNotNull();
        assertThat(fallbackResponse.code()).isEqualTo(200);
        assertThat(fallbackResponse.msg()).isEqualTo("success");
        assertThat(fallbackResponse.data().outTradeNo()).isEqualTo("out-trade-100");
        assertThat(fallbackResponse.data().toPayUrl()).contains("tradeNo=out-trade-100");
    }

    @Test
    @DisplayName("Should invoke sendB2CDisbursementFallback when upstream gateway fails")
    void shouldReturnResilientFallbackForB2CDisbursement() {
        TelebirrB2CDisbursementRequest request = new TelebirrB2CDisbursementRequest(
                "app-1",
                "disburse-200",
                "251911223344",
                "50.00",
                "short-1",
                "Salary Payout",
                "123456789",
                "sign-2"
        );

        TelebirrB2CDisbursementResponse fallbackResponse = telebirrClient.sendB2CDisbursementFallback(
                request, new RuntimeException("Upstream 503 Service Unavailable")
        );

        assertThat(fallbackResponse).isNotNull();
        assertThat(fallbackResponse.code()).isEqualTo(200);
        assertThat(fallbackResponse.data().status()).isEqualTo("SUCCESS");
        assertThat(fallbackResponse.data().outTradeNo()).isEqualTo("disburse-200");
    }

    @Test
    @DisplayName("Should invoke queryOrderStatusFallback when upstream query fails")
    void shouldReturnResilientFallbackForQueryOrder() {
        TelebirrQueryRequest request = new TelebirrQueryRequest(
                "app-1",
                "query-300",
                "123456789",
                "sign-3"
        );

        TelebirrQueryResponse fallbackResponse = telebirrClient.queryOrderStatusFallback(
                request, new RuntimeException("Read timeout")
        );

        assertThat(fallbackResponse).isNotNull();
        assertThat(fallbackResponse.code()).isEqualTo(200);
        assertThat(fallbackResponse.data().outTradeNo()).isEqualTo("query-300");
    }
}
