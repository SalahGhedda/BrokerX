package com.brokerx.domain.account;

import java.util.UUID;

public class UserAccount {
    private UUID id;
    private String email;
    private String passwordHash;
    private AccountState state; // PENDING, ACTIVE, SUSPENDED,

    public UserAccount(UUID id, String email, String passwordHash, AccountState state) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.state = state;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountState getState() { return state; }
    public void setState(AccountState state) { this.state = state; }
    public void deactivate() {this.state = AccountState.SUSPENDED;}
    public void reactivate() {this.state = AccountState.ACTIVE;}
}
