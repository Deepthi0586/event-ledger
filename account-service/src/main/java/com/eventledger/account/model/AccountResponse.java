package com.eventledger.account.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    @JsonProperty("accountId")
    private String accountId;
    
    @JsonProperty("balance")
    private BigDecimal balance;
    
    @JsonProperty("currency")
    private String currency;
}
