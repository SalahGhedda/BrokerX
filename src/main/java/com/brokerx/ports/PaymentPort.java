package com.brokerx.ports;
import java.math.BigDecimal;


public interface PaymentPort {
    void settle(BigDecimal amount);
}