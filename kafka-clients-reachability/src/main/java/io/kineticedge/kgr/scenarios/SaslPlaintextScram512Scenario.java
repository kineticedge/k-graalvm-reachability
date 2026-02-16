package io.kineticedge.kgr.scenarios;

import io.kineticedge.kgr.clusters.SaslPlaintextPlainKafkaContainer;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class SaslPlaintextScram512Scenario extends AbstractScenario<SaslPlaintextPlainKafkaContainer>{

    private static final String USER = "scram-512-user";
    private static final String PASSWORD = "scram-512-password";

    // we want to use standard main method, since non-standard requires some discovery
    // within the jvm which causes GraalVM to generate a record for it.
    public static void main(String[] args) {
        var scenario = new SaslPlaintextScram512Scenario();
        scenario.start();
        scenario.scenario();
        scenario.stop();
    }

    public SaslPlaintextScram512Scenario() {
        super(new SaslPlaintextPlainKafkaContainer());
    }

    void scenario() {


        var topics = List.of("test-x");

        createTopics(topics);

        container.createScramUser(ScramMechanism.SCRAM_SHA_512, USER, PASSWORD);

        try (KafkaProducer<String, String> producer = createProducer(credentials(), "none")) {
            producer.send(new ProducerRecord<>("test-x", "bar", "value"));
        }

        try (var consumer = createConsumer(credentials(), RangeAssignor.class, "sample-group-" , topics)) {
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

    public Map<String, Object> credentials() {
        return Map.ofEntries(
                Map.entry(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512"),
                Map.entry(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + USER + "\" password=\"" + PASSWORD + "\";")
        );
    }



}
