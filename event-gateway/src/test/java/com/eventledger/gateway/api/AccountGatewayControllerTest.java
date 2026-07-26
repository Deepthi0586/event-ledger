package com.eventledger.gateway.api;

import com.eventledger.gateway.model.AccountBalanceResponse;
import com.eventledger.gateway.service.AccountServiceClient;
import com.eventledger.gateway.service.AccountServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountGatewayController.class)
@Import(GlobalExceptionHandler.class)
class AccountGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountServiceClient accountServiceClient;

    @Test
    void shouldReturnAccountBalance() throws Exception {

        AccountBalanceResponse response =
                AccountBalanceResponse.builder()
                        .accountId("acct-123")
                        .balance(new BigDecimal("150.00"))
                        .currency("USD")
                        .build();

        when(accountServiceClient.getAccountBalance("acct-123"))
                .thenReturn(response);

        mockMvc.perform(
                        get("/accounts/acct-123/balance")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.accountId")
                                .value("acct-123")
                )
                .andExpect(
                        jsonPath("$.balance")
                                .value(150.00)
                )
                .andExpect(
                        jsonPath("$.currency")
                                .value("USD")
                );
    }

    @Test
    void shouldReturn503WhenAccountServiceIsUnavailable()
            throws Exception {

        when(accountServiceClient.getAccountBalance("acct-123"))
                .thenThrow(
                        new AccountServiceUnavailableException(
                                "Account Service is unavailable"
                        )
                );

        mockMvc.perform(
                        get("/accounts/acct-123/balance")
                )
                .andExpect(
                        status().isServiceUnavailable()
                );
    }
}