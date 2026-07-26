package com.eventledger.gateway.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.service.AccountServiceClient;
import com.eventledger.gateway.util.StructuredLogger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;

@PactConsumerTest
@PactTestFor(providerName = "account-service")
class AccountServiceConsumerPactTest {

    private static final String TRACE_ID = "trace-contract-001";

    @Pact(
            consumer = "event-gateway",
            provider = "account-service"
    )
    V4Pact applyCreditTransactionContract(
            PactDslWithProvider builder) {

        PactDslJsonBody requestBody = new PactDslJsonBody()
                .stringValue("eventId", "evt-contract-001")
                .stringValue("accountId", "acct-123")
                .stringValue("type", "CREDIT")
                .decimalType("amount", 100.00)
                .stringValue("currency", "USD")
                .stringValue(
                        "eventTimestamp",
                        "2026-05-15T14:02:11Z"
                );

        PactDslJsonBody responseBody = new PactDslJsonBody()
                .stringValue("status", "success");

        return builder
                .given("account acct-123 can receive transactions")
                .uponReceiving(
                        "a credit transaction from Event Gateway"
                )
                .path("/accounts/acct-123/transactions")
                .method("POST")
                .headers(Map.of(
                        "Content-Type", "application/json",
                        "X-Trace-ID", TRACE_ID
                ))
                .body(requestBody)
                .willRespondWith()
                .status(200)
                .headers(Map.of(
                        "Content-Type", "application/json"
                ))
                .body(responseBody)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(
            pactMethod = "applyCreditTransactionContract"
    )
    void shouldCallAccountServiceUsingTheContract(
            MockServer mockServer) {

        StructuredLogger structuredLogger =
                mock(StructuredLogger.class);

        AccountServiceClient client =
                new AccountServiceClient(
                        new RestTemplate(),
                        structuredLogger
                );

        ReflectionTestUtils.setField(
                client,
                "accountServiceBaseUrl",
                mockServer.getUrl()
        );

        Event event = new Event();
        event.setEventId("evt-contract-001");
        event.setAccountId("acct-123");
        event.setType(Event.TransactionType.CREDIT);
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("USD");
        event.setEventTimestamp(
                Instant.parse("2026-05-15T14:02:11Z")
        );
        event.setReceivedTimestamp(Instant.now());
        event.setStatus(Event.EventStatus.PENDING);

        client.applyTransaction(event, TRACE_ID);
    }
}