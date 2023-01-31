package com.example.camel.routes.manager.service.camel;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Component
public class SqsDataReplicationRoute extends RouteBuilder {

    public static final String ROUTE_ID = "test-route";
    private static final String SQS_ROUTE_URI = "aws-sqs://%s?amazonSQSClient=#sqsClient&maxMessagesPerPoll=10";

    @Value("${sqs.name}")
    private String sqsName;

    @Override
    public void configure() {
        from(String.format(SQS_ROUTE_URI, sqsName))
            .autoStartup(false)
            .routeId(ROUTE_ID)
            .log("Processor triggered: ${body}")
            .end();
    }

    public String getRouteId() {
        return ROUTE_ID;
    }
}
