package com.eventledger.account.api;

import com.eventledger.account.model.AccountResponse;
import com.eventledger.account.model.TransactionRequest;
import com.eventledger.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    
    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<?> applyTransaction(
            @PathVariable String accountId,
            @RequestBody TransactionRequest request,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        try {
            if (traceId == null) {
                traceId = "unknown";
            }
            
            if (!accountId.equals(request.getAccountId())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Account ID mismatch"
                ));
            }
            
            accountService.applyTransaction(request, traceId);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error"
            ));
        }
    }
    
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountId) {
        try {
            AccountResponse response = accountService.getAccountBalance(accountId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccount(@PathVariable String accountId) {
        try {
            AccountResponse response = accountService.getAccountDetails(accountId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
