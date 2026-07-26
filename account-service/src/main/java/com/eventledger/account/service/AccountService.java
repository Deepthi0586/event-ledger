package com.eventledger.account.service;

import com.eventledger.account.model.Account;
import com.eventledger.account.model.AccountResponse;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.model.TransactionRequest;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Logger logger =
            LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public void applyTransaction(
            TransactionRequest request,
            String traceId) {

        validateTransactionRequest(request);

        logger.info(
                "Applying transaction: {} for account: {} with traceId: {}",
                request.getEventId(),
                request.getAccountId(),
                traceId
        );

        Optional<Transaction> existingTransaction =
                transactionRepository.findByEventId(
                        request.getEventId()
                );

        if (existingTransaction.isPresent()) {
            logger.info(
                    "Transaction already applied: {} traceId: {}",
                    request.getEventId(),
                    traceId
            );
            return;
        }

        Account account = accountRepository
                .findById(request.getAccountId())
                .orElseGet(() ->
                        createAccount(request.getAccountId())
                );

        BigDecimal amount = request.getAmount();

        if ("CREDIT".equals(request.getType())) {
            account.setBalance(
                    account.getBalance().add(amount)
            );
        } else {
            account.setBalance(
                    account.getBalance().subtract(amount)
            );
        }

        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setEventId(request.getEventId());
        transaction.setAccountId(request.getAccountId());
        transaction.setType(request.getType());
        transaction.setAmount(amount);
        transaction.setAppliedAt(Instant.now());

        transactionRepository.save(transaction);

        logger.info(
                "Transaction completed. New balance: {} traceId: {}",
                account.getBalance(),
                traceId
        );
    }

    public AccountResponse getAccountBalance(String accountId) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + accountId
                        )
                );

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .build();
    }

    public AccountResponse getAccountDetails(String accountId) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + accountId
                        )
                );

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .build();
    }

    private void validateTransactionRequest(
            TransactionRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "request body is required"
            );
        }

        if (request.getEventId() == null
                || request.getEventId().isBlank()) {
            throw new IllegalArgumentException(
                    "eventId is required"
            );
        }

        if (request.getAccountId() == null
                || request.getAccountId().isBlank()) {
            throw new IllegalArgumentException(
                    "accountId is required"
            );
        }

        if (request.getType() == null
                || request.getType().isBlank()) {
            throw new IllegalArgumentException(
                    "type is required"
            );
        }

        if (!"CREDIT".equals(request.getType())
                && !"DEBIT".equals(request.getType())) {
            throw new IllegalArgumentException(
                    "type must be CREDIT or DEBIT"
            );
        }

        if (request.getAmount() == null) {
            throw new IllegalArgumentException(
                    "amount is required"
            );
        }

        if (request.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than 0"
            );
        }
    }

    private Account createAccount(String accountId) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency("USD");
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());

        return accountRepository.save(account);
    }
}