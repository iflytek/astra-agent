package com.iflytek.astra.console.toolkit.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * Thread pool utility for managing asynchronous task execution. This class provides a configured
 * thread pool with custom exception handling.
 *
 * @author astra-console-toolkit
 */
public class MyThreadTool {

    /**
     * Thread pool executor with 10 core threads, 20 maximum threads, 30 second keep-alive time, and a
     * LinkedBlockingQueue for queuing tasks. Each thread has a custom uncaught exception handler.
     */
    private static final ThreadPoolExecutor pool = new ThreadPoolExecutor(10, 20, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                    r -> {
                        Thread thread = new Thread(r);
                        thread.setUncaughtExceptionHandler(new CustomUncaughtExceptionHandler());
                        return thread;
                    });

    /**
     * Executes a runnable task using the thread pool.
     *
     * @param runnable the task to execute asynchronously
     */
    public static void execute(Runnable runnable) {
        pool.execute(runnable);
    }
}


/**
 * Custom uncaught exception handler for threads in the thread pool. This handler logs any uncaught
 * exceptions that occur during thread execution.
 *
 * @author astra-console-toolkit
 */
@Slf4j
class CustomUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    /**
     * Handles uncaught exceptions from threads.
     *
     * @param t the thread that threw the exception
     * @param e the uncaught exception
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // Custom exception handling logic
        log.error("thread[{}] occur exception, {}", t.getName(), e.getMessage(), e);
    }
}
