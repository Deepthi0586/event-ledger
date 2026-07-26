package com.eventledger.gateway.service;

import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.model.EventRequest;
import com.eventledger.gateway.model.EventResponse;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.gateway.util.StructuredLogger;
import com.eventledger.gateway.util.TracingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final String SERVICE_NAME = "event-gateway";

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final StructuredLogger structuredLogger;
    private final TracingConfig.TraceIdGenerator traceIdGenerator;
    private final ObjectMapper objectMapper;

    public EventResponse submitEvent(EventRequest request) {
        String traceId = traceIdGenerator.generate();

        try {
            validateEventRequest(request);

            Optional<Event> existingEvent =
                    eventRepository.findByEventId(request.getEventId());

            if (existingEvent.isPresent()) {
                Event existing = existingEvent.get();

                structuredLogger.info(
                        traceId,
                        SERVICE_NAME,
                        "Duplicate event detected",
                        context(
                                "eventId", existing.getEventId(),
                                "status", existing.getStatus()
                        )
                );

                // Idempotency: do not create or apply the transaction again.
                return mapToResponse(existing);
            }

            Event event = createEventFromRequest(request);
            event.setStatus(Event.EventStatus.PENDING);
            event.setReceivedTimestamp(Instant.now());

            Event savedEvent = eventRepository.save(event);

            try {
                accountServiceClient.applyTransaction(savedEvent, traceId);

                savedEvent.setStatus(Event.EventStatus.APPLIED);
                Event appliedEvent = eventRepository.save(savedEvent);

                structuredLogger.info(
                        traceId,
                        SERVICE_NAME,
                        "Event submitted successfully",
                        context(
                                "eventId", appliedEvent.getEventId(),
                                "accountId", appliedEvent.getAccountId(),
                                "status", appliedEvent.getStatus()
                        )
                );

                return mapToResponse(appliedEvent);

            } catch (AccountServiceUnavailableException exception) {
                savedEvent.setStatus(Event.EventStatus.FAILED);
                eventRepository.save(savedEvent);

                structuredLogger.error(
                        traceId,
                        SERVICE_NAME,
                        "Account Service unavailable; event marked as failed",
                        exception,
                        context(
                                "eventId", savedEvent.getEventId(),
                                "accountId", savedEvent.getAccountId()
                        )
                );

                // A global exception handler should map this to HTTP 503.
                throw exception;
            }

        } catch (RuntimeException exception) {
            structuredLogger.error(
                    traceId,
                    SERVICE_NAME,
                    "Error submitting event",
                    exception,
                    context(
                            "eventId",
                            request == null ? null : request.getEventId()
                    )
            );

            throw exception;
        }
    }

    public EventResponse getEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        Event event = eventRepository.findByEventId(eventId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Event not found for eventId: " + eventId
                        )
                );

        return mapToResponse(event);
    }

    public List<EventResponse> getEventsByAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }

        return eventRepository.findByAccountIdOrderByTimestamp(accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void validateEventRequest(EventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }

        if (request.getEventId() == null
                || request.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        if (request.getAccountId() == null
                || request.getAccountId().isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }

        if (request.getType() == null
                || request.getType().isBlank()) {
            throw new IllegalArgumentException("type is required");
        }

        try {
            Event.TransactionType.valueOf(request.getType());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "type must be CREDIT or DEBIT"
            );
        }

        parseAmount(request.getAmount());

        if (request.getCurrency() == null
                || request.getCurrency().isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        if (request.getEventTimestamp() == null
                || request.getEventTimestamp().isBlank()) {
            throw new IllegalArgumentException(
                    "eventTimestamp is required"
            );
        }

        try {
            Instant.parse(request.getEventTimestamp());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "eventTimestamp must be a valid ISO-8601 timestamp"
            );
        }
    }

    private BigDecimal parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            throw new IllegalArgumentException("amount is required");
        }

        try {
            BigDecimal amount = new BigDecimal(rawAmount);

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "amount must be greater than 0"
                );
            }

            return amount;

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "amount must be a valid number"
            );
        }
    }

    private Event createEventFromRequest(EventRequest request) {
        Event event = new Event();

        event.setEventId(request.getEventId());
        event.setAccountId(request.getAccountId());
        event.setType(
                Event.TransactionType.valueOf(request.getType())
        );
        event.setAmount(parseAmount(request.getAmount()));
        event.setCurrency(request.getCurrency());
        event.setEventTimestamp(
                Instant.parse(request.getEventTimestamp())
        );
        event.setMetadata(serializeMetadata(request.getMetadata()));

        return event;
    }

    private String serializeMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "metadata must be valid JSON",
                    exception
            );
        }
    }

    private EventResponse mapToResponse(Event event) {
        return EventResponse.builder()
                .eventId(event.getEventId())
                .accountId(event.getAccountId())
                .type(event.getType().name())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .eventTimestamp(event.getEventTimestamp())
                .receivedTimestamp(event.getReceivedTimestamp())
                .status(event.getStatus().name())
                .metadata(deserializeMetadata(event.getMetadata()))
                .build();
    }

    private Object deserializeMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(metadata, Object.class);
        } catch (JsonProcessingException exception) {
            // Preserve stored content rather than failing a GET request.
            return metadata;
        }
    }

    /**
     * Unlike Map.of(), HashMap allows nullable values.
     */
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