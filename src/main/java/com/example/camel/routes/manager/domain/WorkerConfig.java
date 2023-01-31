package com.example.camel.routes.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Data
@AllArgsConstructor
public class WorkerConfig implements Identifiable<String>, Serializable {

    private String id;
    private WorkerState state;
}
