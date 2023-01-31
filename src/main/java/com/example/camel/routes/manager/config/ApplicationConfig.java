package com.example.camel.routes.manager.config;

import com.example.camel.routes.manager.domain.WorkerConfig;
import com.example.camel.routes.manager.domain.WorkerState;
import com.example.camel.routes.manager.repository.WorkerConfigRepository;
import com.example.camel.routes.manager.service.camel.SqsDataReplicationRoute;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Slf4j
@Configuration
@Import(AwsConfig.class)
public class ApplicationConfig {

    @Autowired
    private String instanceId;

    @Lazy
    @Autowired
    private WorkerConfigRepository workerConfigRepository;

    @Autowired
    private CamelContext camelContext;

    @EventListener(ApplicationReadyEvent.class)
    public void updateWorkerState() {
        WorkerConfig workerConfig = workerConfigRepository.findByIdUnchecked(instanceId);
        if (workerConfig.getState().equals(WorkerState.REPLICATING)) {
            try {
                camelContext.startRoute(SqsDataReplicationRoute.ROUTE_ID);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Bean
    public Map<String, WorkerConfig> workerConfigCache(
        @Value("${cache.invalidation.period}") int cacheInvalidationPeriod) {
        Map<String, WorkerConfig> workerConfigCache = new ConcurrentHashMap<>();
        Executors.newSingleThreadScheduledExecutor()
            .scheduleWithFixedDelay(
                () -> {
                    workerConfigCache.clear();
                    workerConfigRepository.findAll();
                    log.info("Worker config cache is reloaded");
                },
                cacheInvalidationPeriod,
                cacheInvalidationPeriod,
                TimeUnit.SECONDS
            );
        return workerConfigCache;
    }
}
