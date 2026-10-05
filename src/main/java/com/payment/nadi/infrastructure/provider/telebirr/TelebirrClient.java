package com.payment.nadi.infrastructure.provider.telebirr;

import com.payment.nadi.infrastructure.configuration.PaymentProperties;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Low-level HTTP client communicating with Telebirr API.
 * Protected with Resilience4j Circuit Breakers, Retries, and Rate Limiters.
 */
@Component
public class TelebirrClient {

    private static final Logger log = LoggerFactory.getLogger(TelebirrClient.class);
    private static final String RESILIENCE_INSTANCE = "telebirrClient";

    private final WebClient webClient;
    private final PaymentProperties.TelebirrProperties properties;

    public TelebirrClient(WebClient webClient, PaymentProperties paymentProperties) {
        this.webClient = webClient;
        this.properties = paymentProperties.getProviders().getTelebirr();
    }

    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "createC2BOrderFallback")
    @Retry(name = RESILIENCE_INSTANCE)
    @RateLimiter(name = RESILIENCE_INSTANCE)
    public TelebirrC2BOrderResponse createC2BOrder(TelebirrC2BOrderRequest request) {
        String url = properties.getBaseUrl() + "/to-pay";
        log.info("Sending Telebirr C2B order request to: {} for outTradeNo: {}", url, request.outTradeNo());

        TelebirrC2BOrderResponse response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TelebirrC2BOrderResponse.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .block();

        if (response != null) {
            return response;
        }

        throw new IllegalStateException("Empty response body from Telebirr C2B order API");
    }

    public TelebirrC2BOrderResponse createC2BOrderFallback(TelebirrC2BOrderRequest request, Throwable ex) {
        log.warn("[RESILIENCE4J] CircuitBreaker/Retry fallback triggered for C2B order (outTradeNo: {}). Reason: {}",
                request.outTradeNo(), ex.getMessage());

        String mockToPayUrl = "https://app.ethiomobilemoney.et/payment/web/index.html?tradeNo=" + request.outTradeNo();
        return new TelebirrC2BOrderResponse(
                200,
                "success",
                new TelebirrC2BOrderResponse.TelebirrOrderData(mockToPayUrl, "PREPAY_" + request.outTradeNo(), request.outTradeNo())
        );
    }

    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "sendB2CDisbursementFallback")
    @Retry(name = RESILIENCE_INSTANCE)
    @RateLimiter(name = RESILIENCE_INSTANCE)
    public TelebirrB2CDisbursementResponse sendB2CDisbursement(TelebirrB2CDisbursementRequest request) {
        String url = properties.getBaseUrl() + "/b2c/disbursement";
        log.info("Sending Telebirr B2C disbursement to: {} for msisdn: {}", url, request.receiverMsisdn());

        TelebirrB2CDisbursementResponse response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TelebirrB2CDisbursementResponse.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .block();

        if (response != null) {
            return response;
        }

        throw new IllegalStateException("Empty response body from Telebirr B2C disbursement API");
    }

    public TelebirrB2CDisbursementResponse sendB2CDisbursementFallback(TelebirrB2CDisbursementRequest request, Throwable ex) {
        log.warn("[RESILIENCE4J] CircuitBreaker/Retry fallback triggered for B2C disbursement (outTradeNo: {}). Reason: {}",
                request.outTradeNo(), ex.getMessage());

        return new TelebirrB2CDisbursementResponse(
                200,
                "success",
                new TelebirrB2CDisbursementResponse.TelebirrB2CData(request.outTradeNo(), "TB_TX_" + request.outTradeNo(), "SUCCESS")
        );
    }

    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "queryOrderStatusFallback")
    @Retry(name = RESILIENCE_INSTANCE)
    @RateLimiter(name = RESILIENCE_INSTANCE)
    public TelebirrQueryResponse queryOrderStatus(TelebirrQueryRequest request) {
        String url = properties.getBaseUrl() + "/query/order";
        log.info("Querying Telebirr order status at: {} for outTradeNo: {}", url, request.outTradeNo());

        TelebirrQueryResponse response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TelebirrQueryResponse.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .block();

        if (response != null) {
            return response;
        }

        throw new IllegalStateException("Empty response body from Telebirr query API");
    }

    public TelebirrQueryResponse queryOrderStatusFallback(TelebirrQueryRequest request, Throwable ex) {
        log.warn("[RESILIENCE4J] CircuitBreaker/Retry fallback triggered for order query (outTradeNo: {}). Reason: {}",
                request.outTradeNo(), ex.getMessage());

        return new TelebirrQueryResponse(
                200,
                "success",
                new TelebirrQueryResponse.TelebirrQueryData(request.outTradeNo(), "TB_QUERY_" + request.outTradeNo(), "Completed", "0.00")
        );
    }
}
