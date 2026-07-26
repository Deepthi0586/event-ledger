package com.eventledger.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EventSubmissionResult {

    private final EventResponse response;
    private final boolean created;
}