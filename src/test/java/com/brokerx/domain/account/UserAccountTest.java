package com.brokerx.domain.account;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    @Test
    void testCreateAccount() {
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount(userId, "email@example.com", "password", AccountState.ACTIVE);

        assertEquals(userId, account.getId());
        assertEquals(AccountState.ACTIVE, account.getState());
    }

    @Test
    void testDeactivateAccount() {
        UserAccount account = new UserAccount(UUID.randomUUID(), "email@example.com", "password", AccountState.ACTIVE);
        account.deactivate();

        assertEquals(AccountState.SUSPENDED, account.getState());
    }

    @Test
    void testReactivateAccount() {
        UserAccount account = new UserAccount(UUID.randomUUID(), "email@example.com", "password", AccountState.SUSPENDED);
        account.reactivate();

        assertEquals(AccountState.ACTIVE, account.getState());
    }
}
