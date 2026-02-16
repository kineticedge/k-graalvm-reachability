package io.kineticedge.kgr.clusters;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.metrics.JmxReporter;

import java.util.Map;

public class PlaintextKafkaContainer extends DynamicKafkaContainer {

    public PlaintextKafkaContainer() {
        super("EXTERNAL");
        this.withEnv(Map.ofEntries(
                Map.entry("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "EXTERNAL:PLAINTEXT,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT")

        ));

    }

    @Override
    public String clusterId() {
        return "PLAINTEXT-00000--00000";
    }

    @Override
    public Map<String, Object> connectionProperties() {
        return Map.ofEntries(
                Map.entry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers()),
                Map.entry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT"),
                // To ensure JmxReporter is available, enable this in the Plaintext
                Map.entry(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, JmxReporter.class.getName())
        );
    }

}
