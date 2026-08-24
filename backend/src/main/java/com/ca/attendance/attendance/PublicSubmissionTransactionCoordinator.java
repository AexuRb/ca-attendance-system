package com.ca.attendance.attendance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
final class PublicSubmissionTransactionCoordinator {
    private static final int DEFAULT_LOCK_STRIPES = 64;

    private final TransactionOperations transactions;
    private final ReentrantLock[] locks;

    @Autowired
    PublicSubmissionTransactionCoordinator(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions = transactionTemplate;
        this.locks = createLocks(DEFAULT_LOCK_STRIPES);
    }

    private PublicSubmissionTransactionCoordinator(TransactionOperations transactions, int lockStripes) {
        this.transactions = transactions;
        this.locks = createLocks(lockStripes);
    }

    private static ReentrantLock[] createLocks(int lockStripes) {
        if (lockStripes < 1) {
            throw new IllegalArgumentException("锁条带数量必须大于 0");
        }
        ReentrantLock[] locks = new ReentrantLock[lockStripes];
        for (int index = 0; index < lockStripes; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    static PublicSubmissionTransactionCoordinator forTesting(
            TransactionOperations transactions,
            int lockStripes
    ) {
        return new PublicSubmissionTransactionCoordinator(transactions, lockStripes);
    }

    <T> T execute(String requestId, Supplier<T> work) {
        ReentrantLock lock = locks[Math.floorMod(requestId.hashCode(), locks.length)];
        lock.lock();
        try {
            return transactions.execute(status -> work.get());
        } finally {
            lock.unlock();
        }
    }
}
