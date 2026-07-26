package com.eventledger.account.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {
    @JsonProperty("accountId")
    private String accountId;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("eventId")
    private String eventId;
}
