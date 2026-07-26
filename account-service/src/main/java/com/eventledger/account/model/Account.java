package com.eventledger.account.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    @Id
    private String accountId;
    
    private BigDecimal balance = BigDecimal.ZERO;
    private String currency = "USD";
    private Instant createdAt;
    private Instant updatedAt;
}
