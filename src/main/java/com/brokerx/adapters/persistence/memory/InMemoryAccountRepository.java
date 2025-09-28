package com.brokerx.adapters.persistence.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.domain.account.AccountState;
import com.brokerx.domain.account.UserAccount;
import com.brokerx.ports.AccountRepository;

public class InMemoryAccountRepository implements AccountRepository {
    private final Map<UUID, UserAccount> store = new HashMap<>();

    public Optional<UserAccount> findByEmail(String email) {
        return store.values().stream()
                .filter(acc -> acc.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }
    public void save(UserAccount account) { store.put(account.getId(), account); }
    public void activate(UUID accountId) {
        var acc = store.get(accountId);
        if (acc != null) acc.setState(AccountState.ACTIVE);
    }
}
