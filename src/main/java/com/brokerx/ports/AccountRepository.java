package com.brokerx.ports;
import java.util.Optional;
import java.util.UUID;
import com.brokerx.domain.account.UserAccount;


public interface AccountRepository {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByPhone(String phone);
    Optional<UserAccount> findById(UUID accountId);
    void save(UserAccount account);
    void update(UserAccount account);
}
