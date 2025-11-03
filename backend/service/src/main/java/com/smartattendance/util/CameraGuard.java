package com.smartattendance.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple process-wide guard to ensure only one camera user at a time.
 * Prevents multiple VideoCapture instances from opening concurrently,
 * which can crash or freeze some drivers.
 */
public final class CameraGuard {
    private static final ReentrantLock LOCK = new ReentrantLock();

    private CameraGuard() {}

    /**
     * Wrapper representing an acquired camera lock. Implements
     * {@link AutoCloseable} so it can be used with try-with-resources.
     */
    public static final class Lock implements AutoCloseable {
        private final boolean held;

        private Lock(long timeoutMs) {
            boolean acquired;
            try {
                acquired = LOCK.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                acquired = false;
            }
            this.held = acquired;
        }

        public boolean isHeld() {
            return held;
        }

        @Override
        public void close() {
            if (held && LOCK.isHeldByCurrentThread()) {
                LOCK.unlock();
            }
        }
    }

    /** Attempts to acquire the camera guard within the timeout and returns a lock. */
    public static Lock acquire(long timeoutMs) {
        Lock lock = new Lock(timeoutMs);
        return lock.isHeld() ? lock : null;
    }
}

