package io.kineticedge.kgr.scenarios;

import io.kineticedge.kgr.clusters.DynamicKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public abstract class AbstractScenario<T extends DynamicKafkaContainer> {

    protected T container;

    protected AbstractScenario(T container) {
        this.container = container;
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    protected AdminClient createAdmin() {
        final Map<String, Object> config = new HashMap<>(container.connectionProperties());
        return AdminClient.create(config);
    }


    protected KafkaProducer<String, String> createProducer(final String compressionType) {
        return createProducer(null, compressionType);
    }

    protected KafkaProducer<String, String> createProducer(Map<String, Object> additionalProperties, final String compressionType) {
        final Map<String, Object> config = new HashMap<>(container.connectionProperties());
        config.putAll(
                Map.ofEntries(
                        Map.entry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()),
                        Map.entry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()),
                        Map.entry(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType)
                )
        );
        if (additionalProperties != null) {
            config.putAll(additionalProperties);
        }
        return new KafkaProducer<>(config);
    }

    protected KafkaConsumer<String, String> createConsumer(Class<?> partitionAssignmentStrategy, final String groupId, final List<String> topics) {
        return createConsumer(null, partitionAssignmentStrategy, groupId, topics);
    }

    protected KafkaConsumer<String, String> createConsumer(Map<String, Object> additionalProperties, Class<?> partitionAssignmentStrategy, final String groupId, final List<String> topics) {
        final Map<String, Object> config = new HashMap<>(container.connectionProperties());
        config.putAll(
                Map.ofEntries(
                        Map.entry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()),
                        Map.entry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()),
                        Map.entry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                        Map.entry(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, partitionAssignmentStrategy.getName()),
                        Map.entry(ConsumerConfig.GROUP_ID_CONFIG, groupId)
                )
        );
        if (additionalProperties != null) {
            config.putAll(additionalProperties);
        }
        return new KafkaConsumer<>(config);
    }

    protected void createTopics(final List<String> topics) {
        try (AdminClient admin = createAdmin()) {
            var newTopics = topics.stream().map(t -> new NewTopic(t, 1, (short) 1)).toList();
            admin.createTopics(newTopics).all().get();
        } catch (TopicExistsException e) {
            // ignore
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            new RuntimeException(e);
        }
    }
}
