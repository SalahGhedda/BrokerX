package com.brokerx.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.brokerx.domain.wallet.Transaction;
import com.brokerx.ports.PaymentPort;
import com.brokerx.ports.TransactionRepository;
import com.brokerx.ports.WalletRepository;

public class WalletService {
    private final WalletRepository walletRepository;
    private final TransactionRepository txRepository;
    private final PaymentPort paymentPort;

    public WalletService(WalletRepository walletRepository, TransactionRepository txRepository, PaymentPort paymentPort) {
        this.walletRepository = walletRepository;
        this.txRepository = txRepository;
        this.paymentPort = paymentPort;
    }

    public Transaction deposit(UUID ownerId, String idempotencyKey, double amount) {
        var existing = txRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        var wallet = walletRepository.findByOwnerId(ownerId).orElseGet(() -> walletRepository.create(ownerId));

        var amt = BigDecimal.valueOf(amount);
        var pending = Transaction.pending(wallet.getId(), amt, idempotencyKey);
        txRepository.append(pending);

        paymentPort.settle(amt); // mock: succès immédiat

        var settled = pending.settled();
        txRepository.append(settled);

        wallet.credit(amt); // in-memory : on modifie l'objet
        return settled;
    }
}
