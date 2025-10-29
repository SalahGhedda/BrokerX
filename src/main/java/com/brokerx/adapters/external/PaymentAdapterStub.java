package com.brokerx.adapters.external;

import java.math.BigDecimal;

import com.brokerx.ports.PaymentPort;

/**
 * Stub implementation of the external payment processor.
 * For the prototype it only logs the settlement outcome.
 */
public class PaymentAdapterStub implements PaymentPort {
    @Override
    public void settle(BigDecimal amount) {
        System.out.println("Paiement simule SETTLED pour " + amount + " $");
    }
}
