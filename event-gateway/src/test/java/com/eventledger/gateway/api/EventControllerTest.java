package com.eventledger.gateway.api;

import com.eventledger.gateway.model.EventRequest;
import com.eventledger.gateway.model.EventResponse;
import com.eventledger.gateway.model.EventSubmissionResult;
import com.eventledger.gateway.service.AccountServiceUnavailableException;
import com.eventledger.gateway.service.EventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(GlobalExceptionHandler.class)
class EventControllerTest {

    private static final String VALID_EVENT_JSON = """
            {
              "eventId": "evt-001",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 150.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z",
              "metadata": {
                "source": "controller-test"
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @Test
    void shouldReturn201WhenNewEventIsCreated()
            throws Exception {

        EventResponse eventResponse =
                mock(EventResponse.class);

        EventSubmissionResult submissionResult =
                mock(EventSubmissionResult.class);

        when(submissionResult.isCreated())
                .thenReturn(true);

        when(submissionResult.getResponse())
                .thenReturn(eventResponse);

        when(eventService.submitEvent(any(EventRequest.class)))
                .thenReturn(submissionResult);

        mockMvc.perform(
                        post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_EVENT_JSON)
                )
                .andExpect(status().isCreated());

        ArgumentCaptor<EventRequest> requestCaptor =
                ArgumentCaptor.forClass(EventRequest.class);

        verify(eventService)
                .submitEvent(requestCaptor.capture());

        EventRequest capturedRequest =
                requestCaptor.getValue();

        assertEquals(
                "evt-001",
                capturedRequest.getEventId()
        );

        assertEquals(
                "acct-123",
                capturedRequest.getAccountId()
        );

        assertEquals(
                0,
                new BigDecimal("150.00")
                        .compareTo(capturedRequest.getAmount())
        );
    }

    @Test
    void shouldReturn200WhenEventIsDuplicate()
            throws Exception {

        EventResponse eventResponse =
                mock(EventResponse.class);

        EventSubmissionResult submissionResult =
                mock(EventSubmissionResult.class);

        when(submissionResult.isCreated())
                .thenReturn(false);

        when(submissionResult.getResponse())
                .thenReturn(eventResponse);

        when(eventService.submitEvent(any(EventRequest.class)))
                .thenReturn(submissionResult);

        mockMvc.perform(
                        post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_EVENT_JSON)
                )
                .andExpect(status().isOk());

        verify(eventService)
                .submitEvent(any(EventRequest.class));
    }

    @Test
    void shouldReturn503WhenAccountServiceIsUnavailable()
            throws Exception {

        when(eventService.submitEvent(any(EventRequest.class)))
                .thenThrow(
                        new AccountServiceUnavailableException(
                                "Account Service is currently unavailable"
                        )
                );

        mockMvc.perform(
                        post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_EVENT_JSON)
                )
                .andExpect(
                        status().isServiceUnavailable()
                );

        verify(eventService)
                .submitEvent(any(EventRequest.class));
    }

    @Test
    void shouldReturnEventById()
            throws Exception {

        EventResponse eventResponse =
                mock(EventResponse.class);

        when(eventService.getEvent("evt-001"))
                .thenReturn(eventResponse);

        mockMvc.perform(
                        get("/events/evt-001")
                )
                .andExpect(status().isOk());

        verify(eventService)
                .getEvent("evt-001");
    }

    @Test
    void shouldReturn404WhenEventDoesNotExist()
            throws Exception {

        when(eventService.getEvent("evt-missing"))
                .thenReturn(null);

        mockMvc.perform(
                        get("/events/evt-missing")
                )
                .andExpect(status().isNotFound());

        verify(eventService)
                .getEvent("evt-missing");
    }

    @Test
    void shouldReturnEventsForAccount()
            throws Exception {

        EventResponse firstEvent =
                mock(EventResponse.class);

        EventResponse secondEvent =
                mock(EventResponse.class);

        when(eventService.getEventsByAccount("acct-123"))
                .thenReturn(
                        List.of(
                                firstEvent,
                                secondEvent
                        )
                );

        mockMvc.perform(
                        get("/events")
                                .param("account", "acct-123")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        verify(eventService)
                .getEventsByAccount("acct-123");
    }

    @Test
    void shouldReturn400WhenAccountQueryParameterIsMissing()
            throws Exception {

        mockMvc.perform(
                        get("/events")
                )
                .andExpect(status().isBadRequest());
    }
}