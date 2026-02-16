package io.kineticedge.kgr.scenarios;

import io.kineticedge.kgr.clusters.SslKafkaContainer;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class SslScenario extends AbstractScenario<SslKafkaContainer>{

    // we want to use standard main method, since non-standard requires some discovery
    // within the jvm which causes GraalVM to generate a record for it.
    public static void main(String[] args) {
        var scenario = new SslScenario();
        scenario.start();
        scenario.scenario();
        scenario.stop();
    }

    public SslScenario() {
        super(new SslKafkaContainer());
    }

    void scenario() {


        var topics = List.of("test-x");

        createTopics(topics);

        try (KafkaProducer<String, String> producer = createProducer("none")) {
            producer.send(new ProducerRecord<>("test-x", "bar", "value"));
        }

        try (var consumer = createConsumer(RangeAssignor.class, "sample-group-" , topics)) {
            final long start = System.currentTimeMillis();
            AtomicLong counter = new AtomicLong(0);
            consumer.subscribe(topics);
            while (counter.get() < 1) {
                var records = consumer.poll(Duration.ofMillis(200L));
                records.forEach(_ -> {
                    counter.incrementAndGet();
                });
                if (System.currentTimeMillis() - start > 20_000L) {
                    throw new RuntimeException("issues during consumer execution, not all messages consumed.");
                }
            }
        }
    }

}
