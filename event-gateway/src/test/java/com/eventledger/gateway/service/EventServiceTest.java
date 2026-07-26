package com.eventledger.gateway.service;

import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.model.EventRequest;
import com.eventledger.gateway.model.EventResponse;
import com.eventledger.gateway.model.EventSubmissionResult;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.gateway.util.StructuredLogger;
import com.eventledger.gateway.util.TracingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final String TRACE_ID = "trace-test-001";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private StructuredLogger structuredLogger;

    @Mock
    private TracingConfig.TraceIdGenerator traceIdGenerator;

    private SimpleMeterRegistry meterRegistry;
    private EventService eventService;
    private EventRequest validRequest;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        eventService = new EventService(
                eventRepository,
                accountServiceClient,
                structuredLogger,
                traceIdGenerator,
                new ObjectMapper(),
                meterRegistry
        );

        validRequest = new EventRequest();
        validRequest.setEventId("evt-001");
        validRequest.setAccountId("acct-123");
        validRequest.setType("CREDIT");
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCurrency("USD");
        validRequest.setEventTimestamp("2026-05-15T14:02:11Z");
    }

    @Test
    void submitEventShouldCreateAndApplyNewEvent() {
        stubTraceId();

        when(eventRepository.findByEventId("evt-001"))
                .thenReturn(Optional.empty());

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventSubmissionResult result =
                eventService.submitEvent(validRequest);

        assertTrue(result.isCreated());
        assertNotNull(result.getResponse());
        assertEquals("evt-001", result.getResponse().getEventId());
        assertEquals("acct-123", result.getResponse().getAccountId());
        assertEquals("CREDIT", result.getResponse().getType());
        assertEquals(
                new BigDecimal("100.00"),
                result.getResponse().getAmount()
        );
        assertEquals("USD", result.getResponse().getCurrency());
        assertEquals("APPLIED", result.getResponse().getStatus());
        assertNotNull(result.getResponse().getReceivedTimestamp());

        verify(accountServiceClient, times(1))
                .applyTransaction(any(Event.class), eq(TRACE_ID));

        /*
         * First save stores PENDING.
         * Second save stores APPLIED.
         */
        verify(eventRepository, times(2))
                .save(any(Event.class));

        assertEquals(
                1.0,
                meterRegistry
                        .counter("eventledger.events.submitted")
                        .count()
        );
    }

    @Test
    void submitEventShouldReturnOriginalEventForDuplicate() {
        stubTraceId();

        Event existingEvent = createExistingEvent(
                "evt-001",
                "acct-123",
                Event.EventStatus.APPLIED,
                "2026-05-15T14:02:11Z"
        );

        when(eventRepository.findByEventId("evt-001"))
                .thenReturn(Optional.of(existingEvent));

        EventSubmissionResult result =
                eventService.submitEvent(validRequest);

        assertFalse(result.isCreated());
        assertEquals("evt-001", result.getResponse().getEventId());
        assertEquals("APPLIED", result.getResponse().getStatus());
        assertEquals(
                existingEvent.getReceivedTimestamp(),
                result.getResponse().getReceivedTimestamp()
        );

        verify(accountServiceClient, never())
                .applyTransaction(any(Event.class), anyString());

        verify(eventRepository, never())
                .save(any(Event.class));

        assertEquals(
                1.0,
                meterRegistry
                        .counter("eventledger.events.duplicate")
                        .count()
        );
    }

    @Test
    void submitEventShouldMarkEventFailedWhenAccountServiceIsUnavailable() {
        stubTraceId();

        when(eventRepository.findByEventId("evt-001"))
                .thenReturn(Optional.empty());

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountServiceUnavailableException unavailableException =
                new AccountServiceUnavailableException(
                        "Account Service is currently unavailable",
                        new RuntimeException("Connection refused")
                );

        doThrow(unavailableException)
                .when(accountServiceClient)
                .applyTransaction(
                        any(Event.class),
                        eq(TRACE_ID)
                );

        AccountServiceUnavailableException thrown =
                assertThrows(
                        AccountServiceUnavailableException.class,
                        () -> eventService.submitEvent(validRequest)
                );

        assertSame(unavailableException, thrown);

        verify(accountServiceClient, times(1))
                .applyTransaction(
                        any(Event.class),
                        eq(TRACE_ID)
                );

        /*
         * First save stores PENDING.
         * Second save stores FAILED.
         */
        verify(eventRepository, times(2))
                .save(any(Event.class));

        assertEquals(
                1.0,
                meterRegistry
                        .counter("eventledger.events.failed")
                        .count()
        );
    }

    @Test
    void getEventShouldReturnStoredEvent() {
        Event storedEvent = createExistingEvent(
                "evt-001",
                "acct-123",
                Event.EventStatus.APPLIED,
                "2026-05-15T14:02:11Z"
        );

        when(eventRepository.findByEventId("evt-001"))
                .thenReturn(Optional.of(storedEvent));

        EventResponse response =
                eventService.getEvent("evt-001");

        assertEquals("evt-001", response.getEventId());
        assertEquals("acct-123", response.getAccountId());
        assertEquals("CREDIT", response.getType());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertEquals("APPLIED", response.getStatus());
    }

    @Test
    void getEventShouldThrowWhenEventDoesNotExist() {
        when(eventRepository.findByEventId("missing-event"))
                .thenReturn(Optional.empty());

        NoSuchElementException exception =
                assertThrows(
                        NoSuchElementException.class,
                        () -> eventService.getEvent("missing-event")
                );

        assertEquals(
                "Event not found for eventId: missing-event",
                exception.getMessage()
        );
    }

    @Test
    void getEventsByAccountShouldPreserveTimestampOrder() {
        Event earlierEvent = createExistingEvent(
                "evt-earlier",
                "acct-123",
                Event.EventStatus.APPLIED,
                "2026-05-15T10:00:00Z"
        );

        Event laterEvent = createExistingEvent(
                "evt-later",
                "acct-123",
                Event.EventStatus.APPLIED,
                "2026-05-15T15:00:00Z"
        );

        when(eventRepository.findByAccountIdOrderByTimestamp("acct-123"))
                .thenReturn(List.of(earlierEvent, laterEvent));

        List<EventResponse> responses =
                eventService.getEventsByAccount("acct-123");

        assertEquals(2, responses.size());
        assertEquals("evt-earlier", responses.get(0).getEventId());
        assertEquals("evt-later", responses.get(1).getEventId());

        assertTrue(
                responses.get(0)
                        .getEventTimestamp()
                        .isBefore(responses.get(1).getEventTimestamp())
        );
    }

    @Test
    void submitEventShouldRejectMissingEventId() {
        stubTraceId();
        validRequest.setEventId(null);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> eventService.submitEvent(validRequest)
                );

        assertEquals("eventId is required", exception.getMessage());
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(accountServiceClient);
    }

    @Test
    void submitEventShouldRejectUnknownTransactionType() {
        stubTraceId();
        validRequest.setType("INVALID");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> eventService.submitEvent(validRequest)
                );

        assertEquals(
                "type must be CREDIT or DEBIT",
                exception.getMessage()
        );

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(accountServiceClient);
    }

    @Test
    void submitEventShouldRejectZeroAmount() {
        stubTraceId();
        validRequest.setAmount(BigDecimal.ZERO);
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> eventService.submitEvent(validRequest)
                );

        assertEquals(
                "amount must be greater than 0",
                exception.getMessage()
        );

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(accountServiceClient);
    }

    @Test
    void submitEventShouldRejectNegativeAmount() {
        stubTraceId();
        validRequest.setAmount(new BigDecimal("-50.00"));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> eventService.submitEvent(validRequest)
                );

        assertEquals(
                "amount must be greater than 0",
                exception.getMessage()
        );

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(accountServiceClient);
    }

    @Test
    void submitEventShouldRejectInvalidTimestamp() {
        stubTraceId();
        validRequest.setEventTimestamp("not-a-timestamp");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> eventService.submitEvent(validRequest)
                );

        assertEquals(
                "eventTimestamp must be a valid ISO-8601 timestamp",
                exception.getMessage()
        );

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(accountServiceClient);
    }

    private void stubTraceId() {
        when(traceIdGenerator.generate()).thenReturn(TRACE_ID);
    }

    private Event createExistingEvent(
            String eventId,
            String accountId,
            Event.EventStatus status,
            String eventTimestamp) {

        Event event = new Event();
        event.setEventId(eventId);
        event.setAccountId(accountId);
        event.setType(Event.TransactionType.CREDIT);
        event.setAmount(new BigDecimal("100.00"));
        event.setCurrency("USD");
        event.setEventTimestamp(Instant.parse(eventTimestamp));
        event.setReceivedTimestamp(
                Instant.parse("2026-07-26T04:30:00Z")
        );
        event.setStatus(status);
        event.setMetadata(null);

        return event;
    }
}