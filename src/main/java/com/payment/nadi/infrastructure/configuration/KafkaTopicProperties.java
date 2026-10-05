package com.payment.nadi.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicProperties {

    private String paymentCompleted = "payment.completed";
    private String paymentFailed = "payment.failed";
    private String paymentInitiated = "payment.initiated";
    private String transferCompleted = "payment.transfer-completed";
    private String allEvents = "payment.events";
    private int partitions = 3;
    private short replicationFactor = 1;
}
