package com.eventledger.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequest {

    private String eventId;
    private String accountId;
    private String type;
    private String amount;
    private String currency;
    private String eventTimestamp;
    private Map<String, Object> metadata;
}