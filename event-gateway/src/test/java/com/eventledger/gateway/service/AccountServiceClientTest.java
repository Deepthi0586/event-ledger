package com.eventledger.gateway.service;

import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.util.StructuredLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountServiceClientTest {

    private static final String ACCOUNT_SERVICE_URL =
            "http://localhost:8081";

    private static final String TRACE_ID =
            "trace-test-001";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private AccountServiceClient accountServiceClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();

        mockServer = MockRestServiceServer
                .bindTo(restTemplate)
                .build();

        StructuredLogger structuredLogger =
                mock(StructuredLogger.class);

        accountServiceClient = new AccountServiceClient(
                restTemplate,
                structuredLogger
        );

        ReflectionTestUtils.setField(
                accountServiceClient,
                "accountServiceBaseUrl",
                ACCOUNT_SERVICE_URL
        );
    }

    @Test
    void applyTransactionShouldSendTraceIdAndTransactionPayload() {
        Event event = createEvent();

        mockServer.expect(
                        once(),
                        requestTo(
                                ACCOUNT_SERVICE_URL
                                        + "/accounts/acct-123/transactions"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Trace-ID", TRACE_ID))
                .andExpect(
                        header(
                                "Content-Type",
                                "application/json"
                        )
                )
                .andExpect(
                        content().json(
                                """
                                {
                                  "eventId": "evt-001",
                                  "accountId": "acct-123",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """
                        )
                )
                .andRespond(withSuccess());

        accountServiceClient.applyTransaction(
                event,
                TRACE_ID
        );

        mockServer.verify();
    }

    @Test
    void applyTransactionShouldThrowWhenAccountServiceReturnsServerError() {
        Event event = createEvent();

        mockServer.expect(
                        once(),
                        requestTo(
                                ACCOUNT_SERVICE_URL
                                        + "/accounts/acct-123/transactions"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Trace-ID", TRACE_ID))
                .andRespond(withServerError());

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountServiceClient.applyTransaction(
                        event,
                        TRACE_ID
                )
        );

        mockServer.verify();
    }

    @Test
    void applyTransactionShouldThrowForNonSuccessfulResponse() {
        Event event = createEvent();

        mockServer.expect(
                        once(),
                        requestTo(
                                ACCOUNT_SERVICE_URL
                                        + "/accounts/acct-123/transactions"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Trace-ID", TRACE_ID))
                .andRespond(
                        withStatus(HttpStatus.BAD_GATEWAY)
                );

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountServiceClient.applyTransaction(
                        event,
                        TRACE_ID
                )
        );

        mockServer.verify();
    }

    private Event createEvent() {
        Event event = new Event();

        event.setEventId("evt-001");
        event.setAccountId("acct-123");
        event.setType(Event.TransactionType.CREDIT);
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("USD");
        event.setEventTimestamp(
                Instant.parse("2026-05-15T14:02:11Z")
        );
        event.setReceivedTimestamp(
                Instant.parse("2026-07-26T04:30:00Z")
        );
        event.setStatus(Event.EventStatus.PENDING);

        return event;
    }
}