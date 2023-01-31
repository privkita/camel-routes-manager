package com.example.camel.routes.manager.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Slf4j
@RequiredArgsConstructor
public class RecoveryTask implements Runnable {

    private final LocalDateTime from;
    private final LocalDateTime to;
    private final CountDownLatch recoveryCanBeStarted;
    private final CountDownLatch recoveryCompleted;
    private final CountDownLatch replicationCanBeStarted;

    @Override
    public void run() {
        try {
            log.info("Ready to start recovery");
            recoveryCanBeStarted.await();
            log.info("Recovery started");
            // do some recovery in the [from; to] range
            Thread.sleep(new Random().nextInt(5_000) + 1_000);
            log.info("Recovery completed");
        } catch (InterruptedException e) {
            log.warn("There's an exception occurred", e);
        }
        replicationCanBeStarted.countDown();
        recoveryCompleted.countDown();
    }
}
