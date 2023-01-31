package com.example.camel.routes.manager.config;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.util.EC2MetadataUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p/>
 * Date: 01/30/2023
 *
 * @author Dzmitry Dziokin
 */
@Configuration
public class AwsConfig {

    @Value("${is.local}")
    private boolean isLocal;

    @Bean
    public String instanceId() {
        return isLocal ? "localhost" : EC2MetadataUtils.getInstanceId();
    }

    @Bean
    public AmazonSQS sqsClient() {
        return AmazonSQSClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .build();
    }

}
