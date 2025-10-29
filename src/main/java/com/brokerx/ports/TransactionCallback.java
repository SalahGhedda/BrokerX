package com.brokerx.ports;

@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction() throws Exception;
}
