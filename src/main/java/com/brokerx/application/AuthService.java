package com.brokerx.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import com.brokerx.domain.account.AccountState;
import com.brokerx.domain.account.UserAccount;
import com.brokerx.ports.AccountRepository;
import com.brokerx.ports.WalletRepository;

public class AuthService {
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;

    public AuthService(AccountRepository accountRepository, WalletRepository walletRepository) {
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
    }

    public UUID createAccount(String email, String passwordPlain) {
        var id = UUID.randomUUID();
        var acc = new UserAccount(id, email, hashPassword(passwordPlain), AccountState.PENDING);
        accountRepository.save(acc);
        walletRepository.create(id); // créer un wallet à l'inscription
        return id;
    }

    public void confirmAccount(UUID accountId) {
        accountRepository.activate(accountId);
    }

    private String hashPassword(String passwordPlain) {
    try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(passwordPlain.getBytes());
        return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not available", e);
    }
}
}
