package com.example.camel.routes.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * <p/>
 * Date: 01/31/2023
 *
 * @author Dzmitry Dziokin
 */
@Configuration
public class ExecutorsConfig {

    @Bean
    public ScheduledExecutorService clusterStateExecutorService() {
        return Executors.newScheduledThreadPool(1);
    }

    @Bean
    public ExecutorService recoveryExecutorService(Integer recoveryWorkersCount) {
        return Executors.newFixedThreadPool(recoveryWorkersCount);
    }

    @Bean
    public Integer recoveryWorkersCount() {
        return Runtime.getRuntime().availableProcessors();
    }
}
