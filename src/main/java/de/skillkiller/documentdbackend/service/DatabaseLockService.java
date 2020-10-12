package de.skillkiller.documentdbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DatabaseLockService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseLockService.class);
    public final ReentrantLock lock = new ReentrantLock();
    public final Phaser phaser = new Phaser(0);

    public void requestDoingWriteOperation() throws InterruptedException, TimeoutException {
        boolean timeout = !lock.tryLock(10, TimeUnit.SECONDS);
        if (!timeout) {
            lock.unlock();
            phaser.register();
        } else {
            throw new TimeoutException();
        }
    }

    public void completeWriteOperation() {
        phaser.arriveAndDeregister();
    }

    public void waitForAllWriteOperationsToFinish(final long timeoutMillis) throws InterruptedException, TimeoutException {
        if (phaser.getUnarrivedParties() == 0) return;
        phaser.awaitAdvanceInterruptibly(0, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void lockNewWriteOperations() {
        lock.lock();
        logger.info("Locked new write operations");
    }

    public void unlockNewWriteOperations() {
        lock.unlock();
        logger.info("Unlocked new write operations");

    }

}
