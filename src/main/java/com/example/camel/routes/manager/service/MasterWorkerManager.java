package com.example.camel.routes.manager.service;

import com.example.camel.routes.manager.domain.WorkerConfig;
import com.example.camel.routes.manager.domain.WorkerState;
import com.example.camel.routes.manager.repository.WorkerConfigRepository;
import com.example.camel.routes.manager.service.camel.SqsDataReplicationRoute;
import com.example.camel.routes.manager.service.task.RecoveryTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Slf4j
@Component
public class MasterWorkerManager {

    private final AtomicReference<WorkerState> workerState = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> recoveryCanBeStarted = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> recoveryCompleted = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> replicationCanBeStarted = new AtomicReference<>();
    private final AtomicBoolean recoveryPlanned = new AtomicBoolean(false);
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
    private final AtomicBoolean replicationInProgress = new AtomicBoolean(false);
    private final ScheduledExecutorService clusterStateExecutorService;
    private final ExecutorService recoveryExecutorService;
    private final WorkerConfigRepository workerConfigRepository;
    private final CamelContext camelContext;
    private final String instanceId;
    private final int recoveryWorkersCount;
    private final int clusterStateCheckerRate;

    @Autowired
    public MasterWorkerManager(
        ScheduledExecutorService clusterStateExecutorService,
        ExecutorService recoveryExecutorService,
        WorkerConfigRepository workerConfigRepository,
        CamelContext camelContext,
        int recoveryWorkersCount,
        String instanceId,
        @Value("${cluster.state.checher.rate}") int clusterStateCheckerRate
    ) {
        this.clusterStateExecutorService = clusterStateExecutorService;
        this.recoveryWorkersCount = recoveryWorkersCount;
        this.recoveryExecutorService = recoveryExecutorService;
        this.workerConfigRepository = workerConfigRepository;
        this.camelContext = camelContext;
        this.instanceId = instanceId;
        this.clusterStateCheckerRate = clusterStateCheckerRate;
    }

    @PostConstruct
    void setUp() {
        Set<WorkerState> initialClusterState = getClusterState();
        WorkerState initialWorkerState =
            initialClusterState.contains(WorkerState.RECOVERY) ? WorkerState.RECOVERY : WorkerState.REPLICATING;
        workerConfigRepository.findById(instanceId)
            .ifPresentOrElse(
                workerConfig -> workerConfigRepository.update(new WorkerConfig(instanceId, initialWorkerState)),
                () -> workerConfigRepository.insert(new WorkerConfig(instanceId, initialWorkerState))
            );
        workerState.set(initialWorkerState);
        replicationInProgress.set(!initialWorkerState.equals(WorkerState.RECOVERY));
        launchClusterManager();
        log.info("Cluster Manager is up & running...");
    }

    private void launchClusterManager() {
        clusterStateExecutorService.scheduleAtFixedRate(
            () -> {
                Set<WorkerState> currentClusterState = getClusterState();
                WorkerState currentWorkerState = workerConfigRepository.findByIdUnchecked(instanceId).getState();
                log.debug("Worker manager: workerState({}, {}), clusterState({})",
                    currentWorkerState, workerState.get(), currentClusterState);
                if (shouldStartRecovery(currentClusterState, currentWorkerState)) {
                    recoveryPlanned.set(true);
                    replicationInProgress.set(false);
                    currentWorkerState = suspendSqsRoute(currentWorkerState);
                    submitRecoveryTasks();
                    submitWorkerReplicatingStateUpdate();
                    submitSqsRouteResume();
                }
                if (recoveryPlanned.get() && !recoveryInProgress.get() && clusterReadyForRecovery()) {
                    log.info("Starting recovery tasks...");
                    recoveryCanBeStarted.get().countDown();
                    recoveryInProgress.set(true);
                }

                if (!replicationInProgress.get() && clusterReadyForReplication()) {
                    replicationCanBeStarted.get().countDown();
                }
                workerState.set(currentWorkerState);
            },
            clusterStateCheckerRate,
            clusterStateCheckerRate,
            TimeUnit.SECONDS
        );
    }

    private boolean shouldStartRecovery(Set<WorkerState> currentClusterState, WorkerState currentWorkerState) {
        return workerStateChangedToRecovery(currentWorkerState) ||
            (currentClusterState.contains(WorkerState.RECOVERY) && !recoveryInProgress.get() && !recoveryPlanned.get());
    }

    private WorkerState suspendSqsRoute(WorkerState currentWorkerState) {
        if (currentWorkerState.equals(WorkerState.REPLICATING)) {
            try {
                camelContext.suspendRoute(SqsDataReplicationRoute.ROUTE_ID);
            } catch (Exception e) {
                log.warn("Exception occurred while suspending camel SQS route", e);
                throw new RuntimeException(e);
            }
            currentWorkerState = WorkerState.RECOVERY;
        }
        if (!currentWorkerState.equals(workerState.get())) {
            workerConfigRepository.update(new WorkerConfig(instanceId, currentWorkerState));
        }
        return currentWorkerState;
    }

    private void submitRecoveryTasks() {
        log.info("Submitting recovery tasks: numberOfThreads={}", recoveryWorkersCount);
        if (clusterReadyForRecovery()) {
            recoveryCanBeStarted.set(new CountDownLatch(0));
            recoveryInProgress.set(true);
        } else {
            recoveryCanBeStarted.set(new CountDownLatch(1));
            recoveryInProgress.set(false);
        }
        // '+ 1' is to await cluster is in replication state
        replicationCanBeStarted.set(new CountDownLatch(recoveryWorkersCount + 1));
        recoveryCompleted.set(new CountDownLatch(recoveryWorkersCount));
        for (int i = 0; i < recoveryWorkersCount; i++) {
            recoveryExecutorService.execute(
                new RecoveryTask(null, null,
                    recoveryCanBeStarted.get(), recoveryCompleted.get(), replicationCanBeStarted.get())
            );
        }
    }

    private void submitWorkerReplicatingStateUpdate() {
        recoveryExecutorService.execute(() -> {
            try {
                recoveryCompleted.get().await();
                workerConfigRepository.update(new WorkerConfig(instanceId, WorkerState.REPLICATING));
                recoveryPlanned.set(false);
                log.info("Worker state set to the 'REPLICATING' state");
            } catch (InterruptedException e) {
                log.warn("Exception occurred", e);
            }
        });
    }

    private void submitSqsRouteResume() {
        recoveryExecutorService.execute(() -> {
            try {
                replicationCanBeStarted.get().await();
                log.info("Resuming camel SQS route...");
                camelContext.resumeRoute(SqsDataReplicationRoute.ROUTE_ID);
                recoveryPlanned.set(false);
                recoveryInProgress.set(false);
                replicationInProgress.set(true);
                log.info("Camel SQS route resumed");
            } catch (Exception e) {
                log.warn("Exception occurred while resuming camel SQS route", e);
            }
        });
    }

    private Set<WorkerState> getClusterState() {
        return workerConfigRepository.findAll().stream()
            .map(WorkerConfig::getState)
            .collect(Collectors.toSet());
    }

    private boolean workerStateChangedToRecovery(WorkerState current) {
        return current.equals(WorkerState.RECOVERY) && workerState.get().equals(WorkerState.REPLICATING);
    }

    private boolean clusterReadyForRecovery() {
        return getClusterState().stream()
            .allMatch(state -> state.equals(WorkerState.RECOVERY));
    }

    private boolean clusterReadyForReplication() {
        return getClusterState().stream()
            .allMatch(state -> state.equals(WorkerState.REPLICATING));
    }
}
