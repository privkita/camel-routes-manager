package com.example.camel.routes.manager.domain;

import java.io.Serializable;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
public interface Identifiable<T extends Serializable> extends Serializable {

    T getId();

    void setId(T id);
}
