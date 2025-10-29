package com.brokerx.ports;

@FunctionalInterface
public interface TransactionRunnable {
    void run() throws Exception;
}
