package com.brokerx.adapters.persistence;
import com.brokerx.ports.AccountRepository;
import com.brokerx.domain.account.UserAccount;
import java.util.Optional;
import java.util.UUID;


public class AccountRepositoryJdbc implements AccountRepository {
// TODO: Implémenter JDBC avec HikariCP
    public Optional<UserAccount> findByEmail(String email) { return Optional.empty(); }
    public void save(UserAccount account) { /* TODO */ }
    public void activate(UUID accountId) { /* TODO */ }
}