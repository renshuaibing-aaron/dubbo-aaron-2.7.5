package org.apache.dubbo.common.threadpool.concurrent;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ScheduledCompletableFuture {

    public static <T> CompletableFuture<T> schedule(
            ScheduledExecutorService executor,
            Supplier<T> task,
            long delay,
            TimeUnit unit
    ) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        executor.schedule(
                () -> {
                    try {
                        return completableFuture.complete(task.get());
                    } catch (Throwable t) {
                        return completableFuture.completeExceptionally(t);
                    }
                },
                delay,
                unit
        );
        return completableFuture;
    }

    public static <T> CompletableFuture<T> submit(
            ScheduledExecutorService executor,
            Supplier<T> task
    ) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        executor.submit(
                () -> {
                    try {
                        return completableFuture.complete(task.get());
                    } catch (Throwable t) {
                        return completableFuture.completeExceptionally(t);
                    }
                }
        );
        return completableFuture;
    }

}
