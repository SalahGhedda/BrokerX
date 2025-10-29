package com.brokerx.ports;

public interface TransactionManager {
    <T> T inTransaction(TransactionCallback<T> callback);

    default void inTransaction(TransactionRunnable runnable) {
        inTransaction(() -> {
            runnable.run();
            return null;
        });
    }
}
