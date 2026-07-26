package com.eventledger.gateway.api;

import com.eventledger.gateway.model.AccountBalanceResponse;
import com.eventledger.gateway.service.AccountServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountGatewayController {

    private final AccountServiceClient accountServiceClient;

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @PathVariable String accountId) {

        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException(
                    "accountId is required"
            );
        }

        AccountBalanceResponse response =
                accountServiceClient.getAccountBalance(accountId);

        return ResponseEntity.ok(response);
    }
}