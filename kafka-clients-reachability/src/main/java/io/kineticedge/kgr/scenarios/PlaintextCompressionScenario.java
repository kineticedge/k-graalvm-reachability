package io.kineticedge.kgr.scenarios;

import io.kineticedge.kgr.clusters.PlaintextKafkaContainer;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.consumer.RoundRobinAssignor;
import org.apache.kafka.clients.consumer.StickyAssignor;
import org.apache.kafka.clients.consumer.internals.AbstractPartitionAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PlaintextCompressionScenario extends AbstractScenario<PlaintextKafkaContainer> {

    // we want to use standard main method, since non-standard requires some discovery
    // within the jvm which causes GraalVM to generate a record for it.
    public static void main(String[] args) {
        var scenario = new PlaintextCompressionScenario();
        scenario.start();
        scenario.scenario();
        scenario.stop();
    }

    private static final List<String> COMPRESSION_TYPES = List.of("none", "gzip", "snappy", "lz4", "zstd");

    private static final List<Class<? extends AbstractPartitionAssignor>> ASSIGNORS = List.of(
            RangeAssignor.class,
            RoundRobinAssignor.class,
            StickyAssignor.class,
            CooperativeStickyAssignor.class
    );

    public PlaintextCompressionScenario() {
        super(new PlaintextKafkaContainer());
    }

    void scenario() {

        var topics = COMPRESSION_TYPES.stream().map(t -> "test-" + t).toList();

        createTopics(topics);

        COMPRESSION_TYPES.forEach(compression -> {
            try (KafkaProducer<String, String> producer = createProducer(compression)) {
                producer.send(new ProducerRecord<>("test-" + compression, "bar", "value"));
            }
        });

        ASSIGNORS.forEach(assignor -> {
            try (var consumer = createConsumer(assignor, "sample-group-" + assignor.getSimpleName(), topics)) {
                consumer.subscribe(topics);
                final long start = System.currentTimeMillis();
                final AtomicLong counter = new AtomicLong(0);
                while (counter.get() < COMPRESSION_TYPES.size()) {
                    var records = consumer.poll(Duration.ofMillis(200L));
                    records.forEach(_ -> {
                        counter.incrementAndGet();
                    });
                    if (System.currentTimeMillis() - start > 20_000L) {
                        throw new RuntimeException("issues during consumer execution, not all messages consumed.");
                    }
                }
            }
        });
    }

}
