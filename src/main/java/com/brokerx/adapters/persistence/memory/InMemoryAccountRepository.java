package com.brokerx.adapters.persistence.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.domain.account.UserAccount;
import com.brokerx.ports.AccountRepository;

public class InMemoryAccountRepository implements AccountRepository {
    private final Map<UUID, UserAccount> store = new HashMap<>();

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return store.values().stream()
                .filter(acc -> acc.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public Optional<UserAccount> findByPhone(String phone) {
        return store.values().stream()
                .filter(acc -> acc.getPhone().equalsIgnoreCase(phone))
                .findFirst();
    }

    @Override
    public Optional<UserAccount> findById(UUID accountId) {
        return Optional.ofNullable(store.get(accountId));
    }

    @Override
    public void save(UserAccount account) {
        store.put(account.getId(), account);
    }

    @Override
    public void update(UserAccount account) {
        store.put(account.getId(), account);
    }
}
