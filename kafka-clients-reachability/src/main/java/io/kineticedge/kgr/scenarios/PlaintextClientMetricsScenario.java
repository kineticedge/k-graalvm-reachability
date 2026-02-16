package io.kineticedge.kgr.scenarios;

import io.kineticedge.kgr.clusters.PlaintextCientMetricsKafkaContainer;
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

public class PlaintextClientMetricsScenario extends AbstractScenario<PlaintextCientMetricsKafkaContainer> {

    // we want to use standard main method, since non-standard requires some discovery
    // within the jvm which causes GraalVM to generate a record for it.
    public static void main(String[] args) {
        var scenario = new PlaintextClientMetricsScenario();
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

    public PlaintextClientMetricsScenario() {
        super(new PlaintextCientMetricsKafkaContainer());
    }

    void scenario() {

        var topics = List.of("test-cm");

        container.enableClientMetrics();

        createTopics(topics);

        try (KafkaProducer<String, String> producer = createProducer("none")) {
            producer.send(new ProducerRecord<>("test-cm", "bar", "value"));
            sleep(100L);
            producer.send(new ProducerRecord<>("test-cm", "bar", "value"));
            sleep(100L);
            producer.send(new ProducerRecord<>("test-cm", "bar", "value"));
        }

        try (var consumer = createConsumer(RoundRobinAssignor.class, "sample-group-cm", topics)) {
            consumer.subscribe(topics);
            final long start = System.currentTimeMillis();
            final AtomicLong counter = new AtomicLong(0);
            while (counter.get() < 3) {
                var records = consumer.poll(Duration.ofMillis(200L));
                records.forEach(_ -> {
                    counter.incrementAndGet();
                });
                if (System.currentTimeMillis() - start > 20_000L) {
                    throw new RuntimeException("issues during consumer execution, not all messages consumed.");
                }
            }
            sleep(500);
            // make sure metrics are sent
            consumer.poll(Duration.ofMillis(200L));
        }


        System.out.println("________________________");
        System.out.println(container.getLogs());
    }

}
