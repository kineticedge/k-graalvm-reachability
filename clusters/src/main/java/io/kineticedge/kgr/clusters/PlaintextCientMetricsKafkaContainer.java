package io.kineticedge.kgr.clusters;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.AlterConfigsOptions;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.metrics.JmxReporter;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PlaintextCientMetricsKafkaContainer extends DynamicKafkaContainer {

    public PlaintextCientMetricsKafkaContainer() {
        super("EXTERNAL");

        String metricsReporterJar = Paths.get("../metrics-reporter/build/libs/metrics-reporter-1.0-all.jar")
                .toAbsolutePath()
                .toString();

        this.withEnv(Map.of(
                        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "EXTERNAL:PLAINTEXT,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT",
                        "KAFKA_METRIC_REPORTERS", "io.kineticedge.ksd.metrics.dummy.NoopClientMetricsReporter"

                ))
                .withFileSystemBind(
                        metricsReporterJar,
                        "/opt/kafka/libs/metrics-reporter-1.0-all.jar",
                        org.testcontainers.containers.BindMode.READ_ONLY
                )
        ;

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
                // enable JmxReporter
                Map.entry(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, JmxReporter.class.getName())

        );
    }

    /*
          KAFKA_METRIC_REPORTERS: org.apache.kafka.common.metrics.JmxReporter,io.kineticedge.ksd.metrics.reporter.ClientTelemetryMetricsReporter
      KAFKA_METRIC_REPORTERS_CLIENTTELEMETRY_ENDPOINT: broker-otel:4317
     */


    protected void enableClientMetrics(Admin adminClient) {
        try {

            Map<String, String> configsToBeSet = new HashMap<>();
            configsToBeSet.put("interval.ms", "100");
            configsToBeSet.put("metrics", "org.apache.kafka.");

            ConfigResource configResource = new ConfigResource(
                    ConfigResource.Type.CLIENT_METRICS,
                    "EVERYTHING"
            );

            Collection<AlterConfigOp> alterEntries = configsToBeSet.entrySet().stream()
                    .map(entry -> new AlterConfigOp(
                            new ConfigEntry(entry.getKey(), entry.getValue()),
                            AlterConfigOp.OpType.SET
                    ))
                    .toList();

            adminClient.incrementalAlterConfigs(
                    Collections.singletonMap(configResource, alterEntries),
                    new AlterConfigsOptions().timeoutMs(30000).validateOnly(false)
            ).all().get(30, TimeUnit.SECONDS);

            System.out.println("✓ Client metrics config altered for EVERYTHING");

        } catch (Exception e) {
            System.err.println("failed to enable client metrics: " + e.getMessage());
        }
    }


    public void enableClientMetrics() {
        try (Admin adminClient = createAdmin()) {
            enableClientMetrics(adminClient);
        }
    }

}
