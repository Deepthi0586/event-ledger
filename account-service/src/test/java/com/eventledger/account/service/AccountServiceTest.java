package com.eventledger.account.service;

import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.model.TransactionRequest;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String ACCOUNT_ID = "acct-123";
    private static final String EVENT_ID = "evt-001";
    private static final String TRACE_ID = "trace-001";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private AccountService accountService;
    private TransactionRequest transactionRequest;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                transactionRepository
        );

        transactionRequest = new TransactionRequest();
        transactionRequest.setAccountId(ACCOUNT_ID);
        transactionRequest.setEventId(EVENT_ID);
        transactionRequest.setType("CREDIT");
        transactionRequest.setAmount("100.00");
    }

    @Test
    void applyTransactionShouldIncreaseBalanceForCredit() {
        Account account = existingAccount("500.00");

        when(transactionRepository.findByEventId(EVENT_ID))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account));

        accountService.applyTransaction(
                transactionRequest,
                TRACE_ID
        );

        assertEquals(
                new BigDecimal("600.00"),
                account.getBalance()
        );

        verify(accountRepository, times(1))
                .save(account);

        verify(transactionRepository, times(1))
                .save(any(Transaction.class));
    }

    @Test
    void applyTransactionShouldDecreaseBalanceForDebit() {
        Account account = existingAccount("500.00");

        transactionRequest.setType("DEBIT");
        transactionRequest.setAmount("100.00");

        when(transactionRepository.findByEventId(EVENT_ID))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account));

        accountService.applyTransaction(
                transactionRequest,
                TRACE_ID
        );

        assertEquals(
                new BigDecimal("400.00"),
                account.getBalance()
        );

        verify(accountRepository, times(1))
                .save(account);

        verify(transactionRepository, times(1))
                .save(any(Transaction.class));
    }

    @Test
    void applyTransactionShouldCreateAccountWhenItDoesNotExist() {
        when(transactionRepository.findByEventId(EVENT_ID))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.empty());

        // Return the Account that the service creates
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        accountService.applyTransaction(
                transactionRequest,
                TRACE_ID
        );

        /*
         * First save:
         *   createAccount()
         *
         * Second save:
         *   persist updated balance after applying transaction
         */
        verify(accountRepository, times(2))
                .save(any(Account.class));

        verify(transactionRepository, times(1))
                .save(any(Transaction.class));
    }

    @Test
    void applyTransactionShouldNotApplyDuplicateEventTwice() {
        Transaction existingTransaction = new Transaction();
        existingTransaction.setEventId(EVENT_ID);

        when(transactionRepository.findByEventId(EVENT_ID))
                .thenReturn(Optional.of(existingTransaction));

        accountService.applyTransaction(
                transactionRequest,
                TRACE_ID
        );

        verifyNoInteractions(accountRepository);

        verify(transactionRepository, never())
                .save(any(Transaction.class));
    }

    @Test
    void multipleTransactionsShouldProduceCorrectNetBalance() {
        Account account = existingAccount("500.00");

        TransactionRequest creditRequest = new TransactionRequest();
        creditRequest.setAccountId(ACCOUNT_ID);
        creditRequest.setEventId("evt-credit-001");
        creditRequest.setType("CREDIT");
        creditRequest.setAmount("200.00");

        TransactionRequest debitRequest = new TransactionRequest();
        debitRequest.setAccountId(ACCOUNT_ID);
        debitRequest.setEventId("evt-debit-001");
        debitRequest.setType("DEBIT");
        debitRequest.setAmount("50.00");

        when(transactionRepository.findByEventId("evt-credit-001"))
                .thenReturn(Optional.empty());

        when(transactionRepository.findByEventId("evt-debit-001"))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account));

        accountService.applyTransaction(
                creditRequest,
                TRACE_ID
        );

        accountService.applyTransaction(
                debitRequest,
                TRACE_ID
        );

        assertEquals(
                new BigDecimal("650.00"),
                account.getBalance()
        );

        verify(accountRepository, times(2))
                .save(account);

        verify(transactionRepository, times(2))
                .save(any(Transaction.class));
    }

    private Account existingAccount(String balance) {
        Account account = new Account();
        account.setAccountId(ACCOUNT_ID);
        account.setBalance(new BigDecimal(balance));
        account.setCurrency("USD");
        return account;
    }
}