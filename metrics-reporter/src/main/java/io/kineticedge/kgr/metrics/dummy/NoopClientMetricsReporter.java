package io.kineticedge.kgr.metrics.dummy;

import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsContext;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.apache.kafka.server.telemetry.ClientTelemetry;
import org.apache.kafka.server.telemetry.ClientTelemetryReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class NoopClientMetricsReporter implements MetricsReporter, ClientTelemetry {

  private static final Logger log = LoggerFactory.getLogger(NoopClientMetricsReporter.class);

  private ClientTelemetryReceiver receiver = new NoopTelemetryReceiver();

  @Override
  public void configure(Map<String, ?> configs) {

    System.out.println(">>>>>>>>>>>>>>>>>>>>>>>");
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
    System.out.println("Configuring NoopClientMetricsReporter with configs: " + configs);
  }

  @Override
  public void contextChange(MetricsContext metricsContext) {
    System.out.println(metricsContext.contextLabels());
  }

  @Override
  public void close() {
  }

  /**
   * Using a Client MetricsReporter with Client Telemetry to report on the client metrics sent from the client to the broker.
   */
  @Override
  public ClientTelemetryReceiver clientReceiver() {
    return receiver;
  }

  @Override
  public void init(List<KafkaMetric> metrics) {
    // Not reporting on any of the client metrics (broker side), continue to use an additional metricsreporter, such as the provided JmxReporter.
  }

  @Override
  public void metricChange(KafkaMetric metric) {
    // Not reporting on any of the client metrics (broker side), continue to use an additional metricsreporter, such as the provided JmxReporter.
  }

  @Override
  public void metricRemoval(KafkaMetric metric) {
    // Not reporting on any of the client metrics (broker side), continue to use an additional metricsreporter, such as the provided JmxReporter.
  }

}