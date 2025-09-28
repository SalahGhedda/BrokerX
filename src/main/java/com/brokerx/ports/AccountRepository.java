package com.brokerx.ports;
import java.util.Optional;
import java.util.UUID;
import com.brokerx.domain.account.UserAccount;


public interface AccountRepository {
    Optional<UserAccount> findByEmail(String email);
    void save(UserAccount account);
    void activate(UUID accountId);
}