package com.example.camel.routes.manager.repository;

import com.example.camel.routes.manager.domain.WorkerConfig;
import com.example.camel.routes.manager.domain.WorkerState;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Repository
@RequiredArgsConstructor
public class SpringJdbcWorkerConfigRepository implements WorkerConfigRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final RowMapper<WorkerConfig> workerConfigRowMapper =
        (rs, rowNum) -> new WorkerConfig(rs.getString("id"), WorkerState.valueOf(rs.getString("state")));

    @Override
    public Optional<WorkerConfig> findById(String id) {
        WorkerConfig workerConfig = null;
        try {
            workerConfig = jdbcTemplate.queryForObject(
                "SELECT * FROM public.worker_config WHERE id = :id",
                Map.of("id", id),
                workerConfigRowMapper
            );
        } catch (EmptyResultDataAccessException ignore) {
        }
        return Optional.ofNullable(workerConfig);
    }

    @Override
    public void update(WorkerConfig entity) {
        Map<String, Object> params = Map.of(
            "id", entity.getId(),
            "state", entity.getState().name());
        System.out.println(params);
        jdbcTemplate.update("UPDATE public.worker_config SET state = :state WHERE id = :id", params);
    }

    @Override
    public String insert(WorkerConfig entity) {
        Map<String, Object> params = Map.of(
            "id", entity.getId(),
            "state", entity.getState().name());
        jdbcTemplate.update("INSERT INTO public.worker_config(id, state) VALUES (:id, :state)", params);
        return entity.getId();
    }

    @Override
    public void delete(String s) {

    }

    @Override
    public List<WorkerConfig> findAll() {
        List<Map<String, Object>> workerConfigs =
            jdbcTemplate.queryForList("SELECT * FROM public.worker_config", Collections.EMPTY_MAP);
        return workerConfigs.stream()
            .map(row -> new WorkerConfig(row.get("id").toString(), WorkerState.valueOf(row.get("state").toString())))
            .collect(Collectors.toList());

    }
}
