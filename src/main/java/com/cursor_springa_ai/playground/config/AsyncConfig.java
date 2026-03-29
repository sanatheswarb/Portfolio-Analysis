package com.cursor_springa_ai.playground.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configures async task execution.
 *
 * <p>Uses a virtual-thread-per-task executor (Java 21+) for I/O-bound import
 * pipelines. Virtual threads block cheaply, making them ideal for the
 * sequential NSE API call sequences done per holding during import.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Executor used to fan out per-holding import tasks in parallel.
     * A new virtual thread is created per submitted task — no pool sizing needed.
     */
    @Bean(name = "importExecutor")
    public Executor importExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
