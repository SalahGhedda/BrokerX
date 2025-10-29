package com.brokerx.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserAccountTest {

    @Test
    void activateWithValidCodeTransitionsToActive() {
        var now = Instant.now();
        var account = pendingAccount("123456", now.plusSeconds(3600), now);

        account.activate("123456", now.plusSeconds(10));

        assertEquals(AccountState.ACTIVE, account.getState());
    }

    @Test
    void activateWithWrongCodeFails() {
        var now = Instant.now();
        var account = pendingAccount("123456", now.plusSeconds(3600), now);

        assertThrows(IllegalArgumentException.class, () -> account.activate("000000", now));
    }

    private UserAccount pendingAccount(String code, Instant expiresAt, Instant createdAt) {
        return new UserAccount(
                UUID.randomUUID(),
                "email@example.com",
                "+15145551234",
                "hash",
                "Jane Doe",
                "123 Rue Principale",
                LocalDate.of(1990, 1, 1),
                AccountState.PENDING,
                code,
                expiresAt,
                null,
                createdAt,
                createdAt
        );
    }
}
