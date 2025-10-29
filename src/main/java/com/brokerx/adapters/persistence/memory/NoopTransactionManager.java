package com.brokerx.adapters.persistence.memory;

import com.brokerx.ports.TransactionCallback;
import com.brokerx.ports.TransactionManager;
import com.brokerx.ports.TransactionRunnable;

public class NoopTransactionManager implements TransactionManager {
    @Override
    public <T> T inTransaction(TransactionCallback<T> callback) {
        try {
            return callback.doInTransaction();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void inTransaction(TransactionRunnable runnable) {
        TransactionManager.super.inTransaction(runnable);
    }
}
