package com.example.camel.routes.manager.repository;

import com.example.camel.routes.manager.domain.Identifiable;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
public interface CrudRepository<ID extends Serializable, T extends Identifiable<ID>> {

    default T findByIdUnchecked(ID id) {
        return findById(id).get();
    }

    Optional<T> findById(ID id);

    void update(T entity);

    ID insert(T entity);

    void delete(ID id);

    List<T> findAll();
}
