package com.brokerx.adapters.external;

import java.math.BigDecimal;

import com.brokerx.ports.PaymentPort;

public class PaymentAdapterMock implements PaymentPort {
    @Override
    public void settle(BigDecimal amount) {
        System.out.println("[MOCK] Paiement simulé SETTLED pour " + amount + " $");
    }
}