package io.kineticedge.kgr.scenarios;

import io.kineticedge.kgr.clusters.DynamicKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public abstract class AbstractScenario<T extends DynamicKafkaContainer> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbstractScenario.class);

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


    protected KafkaStreams createStreams(Map<String, Object> additionalProperties, final String appId, final List<String> topics) {
        final Map<String, Object> config = new HashMap<>(container.connectionProperties());
        config.putAll(
                Map.ofEntries(
                        Map.entry(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName()),
                        Map.entry(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName()),
                        Map.entry(StreamsConfig.APPLICATION_ID_CONFIG, appId)
                )
        );
        if (additionalProperties != null) {
            config.putAll(additionalProperties);
        }

        StreamsBuilder builder = new StreamsBuilder();

        builder.stream(topics.get(0))
                .peek((k, v) -> log.info("** k={}, v={}", k, v))
                .to(topics.get(1));

        return new KafkaStreams(builder.build(), toProperties(config));
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


    protected Properties toProperties(Map<String, Object> map) {
        Properties properties = new Properties();
        properties.putAll(map);
        return properties;
    }
}
