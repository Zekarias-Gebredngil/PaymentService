package com.payment.nadi.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    private final KafkaTopicProperties topicProperties;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:payment-service-group}")
    private String groupId;

    public KafkaConfig(KafkaTopicProperties topicProperties, ObjectMapper objectMapper) {
        this.topicProperties = topicProperties;
        this.objectMapper = objectMapper;
    }

    // ==========================================
    // Topic Definitions
    // ==========================================

    @Bean
    @ConditionalOnProperty(prefix = "kafka.admin", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(topicProperties.getPaymentCompleted())
                .partitions(topicProperties.getPartitions())
                .replicas(topicProperties.getReplicationFactor())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kafka.admin", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(topicProperties.getPaymentFailed())
                .partitions(topicProperties.getPartitions())
                .replicas(topicProperties.getReplicationFactor())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kafka.admin", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public NewTopic paymentInitiatedTopic() {
        return TopicBuilder.name(topicProperties.getPaymentInitiated())
                .partitions(topicProperties.getPartitions())
                .replicas(topicProperties.getReplicationFactor())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kafka.admin", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public NewTopic transferCompletedTopic() {
        return TopicBuilder.name(topicProperties.getTransferCompleted())
                .partitions(topicProperties.getPartitions())
                .replicas(topicProperties.getReplicationFactor())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kafka.admin", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public NewTopic allEventsTopic() {
        return TopicBuilder.name(topicProperties.getAllEvents())
                .partitions(topicProperties.getPartitions())
                .replicas(topicProperties.getReplicationFactor())
                .build();
    }

    // ==========================================
    // Kafka Admin
    // ==========================================

    @Bean
    @ConditionalOnProperty(prefix = "kafka.admin", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        KafkaAdmin admin = new KafkaAdmin(configs);
        // Prevent startup failure if Kafka broker is temporarily unreachable
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    // ==========================================
    // Producer Configuration
    // ==========================================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(props);
        factory.setValueSerializer(new JsonSerializer<>(objectMapper));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ==========================================
    // Consumer Configuration
    // ==========================================

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

        JsonDeserializer<Object> jsonDeserializer = new JsonDeserializer<>(objectMapper);
        jsonDeserializer.addTrustedPackages("com.payment.nadi.*", "java.util", "java.lang");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
