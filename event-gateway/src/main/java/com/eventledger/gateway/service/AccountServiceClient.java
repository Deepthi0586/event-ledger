package com.eventledger.gateway.service;

import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.util.StructuredLogger;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
     * This method is public and belongs to a separate Spring bean.
     * The call from EventService therefore passes through Spring's
     * Resilience4j proxy.
     */
    @Retry(name = "accountServiceRetry")
    @CircuitBreaker(
            name = "accountServiceCB",
            fallbackMethod = "fallbackApplyTransaction"
    )
    public void applyTransaction(Event event, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TRACE_ID_HEADER, traceId);

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
            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(transactionRequest, headers);

            ResponseEntity<Void> response =
                    restTemplate.postForEntity(
                            url,
                            entity,
                            Void.class
                    );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AccountServiceUnavailableException(
                        "Account Service returned HTTP "
                                + response.getStatusCode().value()
                );
            }

            structuredLogger.info(
                    traceId,
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

    /**
     * A Resilience4j fallback must match the original parameters,
     * with an optional Throwable parameter at the end.
     */
    public void fallbackApplyTransaction(
            Event event,
            String traceId,
            Throwable throwable
    ) {
        structuredLogger.error(
                traceId,
                SERVICE_NAME,
                "Account Service call failed after resilience handling",
                throwable,
                context(
                        "eventId", event.getEventId(),
                        "accountId", event.getAccountId()
                )
        );

        /*
         * Do not return normally. Returning normally would make the caller
         * believe the transaction succeeded and mark the event APPLIED.
         */
        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable",
                throwable
        );
    }

    private Map<String, Object> context(Object... keyValues) {
        Map<String, Object> values = new HashMap<>();

        for (int index = 0; index < keyValues.length; index += 2) {
            values.put(
                    String.valueOf(keyValues[index]),
                    keyValues[index + 1]
            );
        }

        return values;
    }
}