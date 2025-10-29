package com.brokerx.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.brokerx.domain.account.AccountState;
import com.brokerx.domain.account.UserAccount;
import com.brokerx.ports.AccountAuditRepository;
import com.brokerx.ports.AccountRepository;
import com.brokerx.ports.WalletRepository;

public class AuthService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+0-9][0-9\\- ]{6,}$");
    private static final long VERIFICATION_TTL_SECONDS = 3600;

    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final AccountAuditRepository auditRepository;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AccountRepository accountRepository, WalletRepository walletRepository, AccountAuditRepository auditRepository) {
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
        this.auditRepository = auditRepository;
    }

    public SignupResult register(SignupCommand command) {
        validateSignup(command);
        String normalizedEmail = command.email().trim().toLowerCase();
        String normalizedPhone = command.phone().trim().replaceAll("[\\s-]", "");
        String fullName = command.fullName().trim();
        String addressLine = command.addressLine().trim();
        if (accountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email deja enregistre");
        }
        if (accountRepository.findByPhone(normalizedPhone).isPresent()) {
            throw new IllegalArgumentException("Telephone deja enregistre");
        }

        Instant now = Instant.now();
        String verificationCode = generateVerificationCode();
        Instant expiresAt = now.plusSeconds(VERIFICATION_TTL_SECONDS);
        UUID accountId = UUID.randomUUID();

        UserAccount account = new UserAccount(
                accountId,
                normalizedEmail,
                normalizedPhone,
                hashPassword(command.password()),
                fullName,
                addressLine,
                command.dateOfBirth(),
                AccountState.PENDING,
                verificationCode,
                expiresAt,
                null,
                now,
                now
        );

        accountRepository.save(account);
        walletRepository.create(accountId);
        auditRepository.record(accountId, "ACCOUNT_CREATED", """
            {"source":"self-service","state":"PENDING"}
            """);

        return new SignupResult(accountId, normalizedEmail, verificationCode, expiresAt);
    }

    public void confirmAccount(UUID accountId, String verificationCode) {
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
        account.activate(Objects.requireNonNull(verificationCode, "verificationCode"), Instant.now());
        accountRepository.update(account);
        auditRepository.record(accountId, "ACCOUNT_ACTIVATED", """
            {"method":"self-service"}
            """);
    }

    public Optional<UserAccount> authenticate(String email, String passwordPlain) {
        var hash = hashPassword(passwordPlain);
        return accountRepository.findByEmail(email)
                .filter(acc -> acc.getState() == AccountState.ACTIVE)
                .filter(acc -> acc.getPasswordHash().equals(hash));
    }

    public Optional<UserAccount> findAccount(UUID accountId) {
        return accountRepository.findById(accountId);
    }

    public boolean emailExists(String email) {
        if (email == null) return false;
        return accountRepository.findByEmail(email.trim().toLowerCase()).isPresent();
    }

    private void validateSignup(SignupCommand command) {
        if (command.email() == null || !EMAIL_PATTERN.matcher(command.email().trim()).matches()) {
            throw new IllegalArgumentException("Email invalide");
        }
        if (command.phone() == null || !PHONE_PATTERN.matcher(command.phone().trim()).matches()) {
            throw new IllegalArgumentException("Numero de telephone invalide");
        }
        if (command.password() == null || command.password().length() < 6) {
            throw new IllegalArgumentException("Mot de passe trop court");
        }
        if (command.fullName() == null || command.fullName().isBlank()) {
            throw new IllegalArgumentException("Nom complet requis");
        }
        if (command.addressLine() == null || command.addressLine().isBlank()) {
            throw new IllegalArgumentException("Adresse requise");
        }
        if (command.dateOfBirth() == null) {
            throw new IllegalArgumentException("Date de naissance requise");
        }
    }

    private String generateVerificationCode() {
        int code = random.nextInt(1_000_000);
        return String.format("%06d", code);
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

    public record SignupCommand(
            String email,
            String phone,
            String password,
            String fullName,
            String addressLine,
            LocalDate dateOfBirth
    ) { }

    public record SignupResult(
            UUID accountId,
            String email,
            String verificationCode,
            Instant expiresAt
    ) { }
}
