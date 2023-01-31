package com.example.camel.routes.manager.repository;

import com.example.camel.routes.manager.domain.WorkerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Primary
@Repository
public class CachedWorkerConfigRepository implements WorkerConfigRepository {

    private final Map<String, WorkerConfig> cache;
    private final WorkerConfigRepository delegate;

    public CachedWorkerConfigRepository(
        Map<String, WorkerConfig> cache,
        @Qualifier("springJdbcWorkerConfigRepository") WorkerConfigRepository delegate
    ) {
        this.cache = cache;
        this.delegate = delegate;
    }

    @Override
    public Optional<WorkerConfig> findById(String id) {
        Optional<WorkerConfig> result = Optional.ofNullable(cache.get(id));
        if (result.isEmpty()) {
            result = delegate.findById(id);
            result.ifPresent(foundWorkerConfig -> cache.put(id, foundWorkerConfig));
        }
        return result;
    }

    @Override
    public void update(WorkerConfig entity) {
        delegate.update(entity);
        cache.put(entity.getId(), entity);
    }

    @Override
    public String insert(WorkerConfig entity) {
        String id = delegate.insert(entity);
        cache.put(id, entity);
        return id;
    }

    @Override
    public void delete(String id) {
        delegate.delete(id);
        cache.remove(id);
    }

    @Override
    public List<WorkerConfig> findAll() {
        List<WorkerConfig> values = new ArrayList<>(cache.values());
        if (values.isEmpty()) {
            values = delegate.findAll();
            values.forEach(workerConfig -> cache.put(workerConfig.getId(), workerConfig));
        }
        return values;
    }
}
