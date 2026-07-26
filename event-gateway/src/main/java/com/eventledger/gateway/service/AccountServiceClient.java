package com.eventledger.gateway.service;

import com.eventledger.gateway.model.AccountBalanceResponse;
import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.util.StructuredLogger;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccountServiceClient {

    private static final String SERVICE_NAME = "event-gateway";
    private static final String TRACE_ID_HEADER = "X-Trace-ID";

    private final RestTemplate restTemplate;
    private final StructuredLogger structuredLogger;

    @Value("${account-service.base-url:http://localhost:8081}")
    private String accountServiceBaseUrl;

    /*
     * Sends a financial transaction from Event Gateway
     * to the internal Account Service.
     */
    @Retry(name = "accountServiceRetry")
    @CircuitBreaker(
            name = "accountServiceCB",
            fallbackMethod = "fallbackApplyTransaction"
    )
    public void applyTransaction(Event event, String traceId) {

        String resolvedTraceId = resolveTraceId(traceId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TRACE_ID_HEADER, resolvedTraceId);

        Map<String, Object> transactionRequest = new HashMap<>();
        transactionRequest.put("eventId", event.getEventId());
        transactionRequest.put("accountId", event.getAccountId());
        transactionRequest.put("type", event.getType().name());
        transactionRequest.put("amount", event.getAmount());
        transactionRequest.put("currency", event.getCurrency());
        transactionRequest.put(
                "eventTimestamp",
                event.getEventTimestamp().toString()
        );

        String url = accountServiceBaseUrl
                + "/accounts/"
                + event.getAccountId()
                + "/transactions";

        try {
            HttpEntity<Map<String, Object>> requestEntity =
                    new HttpEntity<>(transactionRequest, headers);

            ResponseEntity<Void> response =
                    restTemplate.postForEntity(
                            url,
                            requestEntity,
                            Void.class
                    );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AccountServiceUnavailableException(
                        "Account Service returned HTTP "
                                + response.getStatusCode().value()
                );
            }

            structuredLogger.info(
                    resolvedTraceId,
                    SERVICE_NAME,
                    "Transaction applied to account",
                    context(
                            "eventId", event.getEventId(),
                            "accountId", event.getAccountId()
                    )
            );

        } catch (RestClientException exception) {
            throw new AccountServiceUnavailableException(
                    "Unable to apply transaction to Account Service",
                    exception
            );
        }
    }

    /*
     * Retrieves the current account balance through
     * the internal Account Service.
     */
    @Retry(name = "accountServiceRetry")
    @CircuitBreaker(
            name = "accountServiceCB",
            fallbackMethod = "getAccountBalanceFallback"
    )
    public AccountBalanceResponse getAccountBalance(
            String accountId) {

        String traceId = resolveTraceId(
                MDC.get("traceId")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(TRACE_ID_HEADER, traceId);

        HttpEntity<Void> requestEntity =
                new HttpEntity<>(headers);

        try {
            ResponseEntity<AccountBalanceResponse> response =
                    restTemplate.exchange(
                            accountServiceBaseUrl
                                    + "/accounts/{accountId}/balance",
                            HttpMethod.GET,
                            requestEntity,
                            AccountBalanceResponse.class,
                            accountId
                    );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AccountServiceUnavailableException(
                        "Account Service returned HTTP "
                                + response.getStatusCode().value()
                );
            }

            AccountBalanceResponse responseBody =
                    response.getBody();

            if (responseBody == null) {
                throw new AccountServiceUnavailableException(
                        "Account Service returned an empty balance response"
                );
            }

            structuredLogger.info(
                    traceId,
                    SERVICE_NAME,
                    "Account balance retrieved",
                    context(
                            "accountId", accountId,
                            "balance", responseBody.getBalance(),
                            "currency", responseBody.getCurrency()
                    )
            );

            return responseBody;

        } catch (RestClientException exception) {
            throw new AccountServiceUnavailableException(
                    "Unable to retrieve balance from Account Service",
                    exception
            );
        }
    }

    /*
     * Fallback for POST transaction requests.
     */
    public void fallbackApplyTransaction(
            Event event,
            String traceId,
            Throwable throwable) {

        String resolvedTraceId = resolveTraceId(traceId);

        structuredLogger.error(
                resolvedTraceId,
                SERVICE_NAME,
                "Account Service call failed after resilience handling",
                throwable,
                context(
                        "eventId", event.getEventId(),
                        "accountId", event.getAccountId()
                )
        );

        /*
         * The fallback must throw an exception.
         * Returning normally would incorrectly mark the event APPLIED.
         */
        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable",
                throwable
        );
    }

    /*
     * Fallback for GET account balance requests.
     */
    public AccountBalanceResponse getAccountBalanceFallback(
            String accountId,
            Throwable throwable) {

        String traceId = resolveTraceId(
                MDC.get("traceId")
        );

        structuredLogger.error(
                traceId,
                SERVICE_NAME,
                "Unable to retrieve account balance",
                throwable,
                context(
                        "accountId", accountId
                )
        );

        throw new AccountServiceUnavailableException(
                "Account Service is unavailable. "
                        + "Balance information cannot be retrieved.",
                throwable
        );
    }

    private String resolveTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "unknown";
        }

        return traceId;
    }

    private Map<String, Object> context(
            Object... keyValues) {

        Map<String, Object> values = new HashMap<>();

        for (int index = 0;
             index < keyValues.length;
             index += 2) {

            values.put(
                    String.valueOf(keyValues[index]),
                    keyValues[index + 1]
            );
        }

        return values;
    }
}