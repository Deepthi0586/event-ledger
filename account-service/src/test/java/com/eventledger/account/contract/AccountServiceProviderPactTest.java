package com.eventledger.account.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.spring6.Spring6MockMvcTestTarget;
import com.eventledger.account.api.AccountController;
import com.eventledger.account.model.TransactionRequest;
import com.eventledger.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;

@WebMvcTest(AccountController.class)
@Provider("account-service")
@PactFolder("../event-gateway/target/pacts")
class AccountServiceProviderPactTest {

    private static final String TRACE_ID = "trace-contract-001";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(
                new Spring6MockMvcTestTarget(mockMvc)
        );
    }

    @State("account acct-123 can receive transactions")
    void accountCanReceiveTransactions() {
        doNothing()
                .when(accountService)
                .applyTransaction(
                        any(TransactionRequest.class),
                        eq(TRACE_ID)
                );
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPactInteraction(
            PactVerificationContext context) {

        context.verifyInteraction();
    }
}