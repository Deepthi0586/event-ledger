package com.eventledger.gateway.api;

import com.eventledger.gateway.model.EventRequest;
import com.eventledger.gateway.model.EventResponse;
import com.eventledger.gateway.model.EventSubmissionResult;
import com.eventledger.gateway.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(
            @RequestBody EventRequest request) {

        EventSubmissionResult result = eventService.submitEvent(request);

        if (result.isCreated()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(result.getResponse());
        }

        return ResponseEntity.ok(result.getResponse());
    }
    
    @GetMapping("/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable String eventId) {
        EventResponse response = eventService.getEvent(eventId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<List<EventResponse>> getEventsByAccount(
            @RequestParam String account) {
        List<EventResponse> responses = eventService.getEventsByAccount(account);
        return ResponseEntity.ok(responses);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "event-gateway"
        ));
    }
}
