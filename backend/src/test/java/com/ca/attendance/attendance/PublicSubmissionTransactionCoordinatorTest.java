package com.ca.attendance.attendance;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSubmissionTransactionCoordinatorTest {
    @Test
    void serializesTransactionsUsingTheSameRequestId() {
        PublicSubmissionTransactionCoordinator coordinator =
                PublicSubmissionTransactionCoordinator.forTesting(
                        TransactionOperations.withoutTransaction(),
                        8
                );
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<String> first = executor.submit(() -> coordinator.execute("same-request", () -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                    return "first";
                }));
                assertTrue(firstEntered.await(2, TimeUnit.SECONDS));

                Future<String> second = executor.submit(() -> {
                    secondStarted.countDown();
                    return coordinator.execute("same-request", () -> {
                        secondEntered.countDown();
                        return "second";
                    });
                });
                assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
                assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));

                releaseFirst.countDown();
                assertEquals("first", first.get(2, TimeUnit.SECONDS));
                assertEquals("second", second.get(2, TimeUnit.SECONDS));
                assertTrue(secondEntered.await(2, TimeUnit.SECONDS));
            }
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待测试信号超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待测试信号被中断", ex);
        }
    }
}
