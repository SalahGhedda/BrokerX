package com.brokerx.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.domain.wallet.Transaction;
import com.brokerx.domain.wallet.Wallet;
import com.brokerx.observability.AppMetrics;
import com.brokerx.observability.StructuredLogger;
import com.brokerx.ports.PaymentPort;
import com.brokerx.ports.TransactionManager;
import com.brokerx.ports.TransactionRepository;
import com.brokerx.ports.WalletRepository;

public class WalletService {
    private static final StructuredLogger LOGGER = StructuredLogger.get(WalletService.class);

    private final WalletRepository walletRepository;
    private final TransactionRepository txRepository;
    private final PaymentPort paymentPort;
    private final TransactionManager transactionManager;

    public WalletService(WalletRepository walletRepository,
                         TransactionRepository txRepository,
                         PaymentPort paymentPort,
                         TransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.txRepository = txRepository;
        this.paymentPort = paymentPort;
        this.transactionManager = transactionManager;
    }

    public Transaction deposit(UUID ownerId, String idempotencyKey, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        BigDecimal depositAmount = BigDecimal.valueOf(amount);
        try {
            Transaction result = transactionManager.inTransaction(() -> {
                var existing = txRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    return existing.get();
                }

                var wallet = walletRepository.findByOwnerId(ownerId).orElseGet(() -> walletRepository.create(ownerId));

                var pending = Transaction.pending(wallet.getId(), depositAmount, idempotencyKey);
                txRepository.append(pending);

                paymentPort.settle(depositAmount); // mock adapter: always succeeds

                var settled = pending.settled();
                txRepository.append(settled);

                wallet.credit(depositAmount);
                walletRepository.update(wallet);
                return settled;
            });
            AppMetrics.recordDeposit(result.getState());
            BigDecimal balance = walletRepository.findByOwnerId(ownerId)
                    .map(Wallet::getBalance)
                    .orElse(BigDecimal.ZERO);
            LOGGER.info("wallet_deposit", Map.of(
                    "accountId", ownerId.toString(),
                    "idempotencyKey", idempotencyKey,
                    "amount", depositAmount.toPlainString(),
                    "state", result.getState(),
                    "balance", balance.toPlainString()
            ));
            return result;
        } catch (RuntimeException ex) {
            AppMetrics.recordDeposit("FAILED");
            LOGGER.error("wallet_deposit_failed", ex, Map.of(
                    "accountId", ownerId.toString(),
                    "idempotencyKey", idempotencyKey,
                    "amount", depositAmount.toPlainString()
            ));
            throw ex;
        }
    }

    public Optional<Wallet> findWallet(UUID ownerId) {
        return walletRepository.findByOwnerId(ownerId);
    }

    public Wallet debit(UUID ownerId, BigDecimal amount) {
        var wallet = walletRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for account " + ownerId));
        wallet.debit(amount);
        walletRepository.update(wallet);
        LOGGER.info("wallet_debit", Map.of(
                "accountId", ownerId.toString(),
                "amount", amount.toPlainString()
        ));
        return wallet;
    }

    public Wallet refund(UUID ownerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return walletRepository.findByOwnerId(ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found for account " + ownerId));
        }
        var wallet = walletRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for account " + ownerId));
        wallet.credit(amount);
        walletRepository.update(wallet);
        LOGGER.info("wallet_refund", Map.of(
                "accountId", ownerId.toString(),
                "amount", amount.toPlainString()
        ));
        return wallet;
    }

    public void ensureBalance(UUID ownerId, BigDecimal required) {
        if (required == null || required.signum() <= 0) {
            throw new IllegalArgumentException("Required amount must be positive");
        }
        var wallet = walletRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for account " + ownerId));
        if (wallet.getBalance().compareTo(required) < 0) {
            LOGGER.warn("wallet_balance_insufficient", Map.of(
                    "accountId", ownerId.toString(),
                    "balance", wallet.getBalance().toPlainString(),
                    "required", required.toPlainString()
            ));
            throw new IllegalArgumentException("Solde insuffisant pour couvrir l'ordre");
        }
        LOGGER.info("wallet_balance_ok", Map.of(
                "accountId", ownerId.toString(),
                "balance", wallet.getBalance().toPlainString(),
                "required", required.toPlainString()
        ));
    }
}
