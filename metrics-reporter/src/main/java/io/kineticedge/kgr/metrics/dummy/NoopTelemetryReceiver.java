package io.kineticedge.kgr.metrics.dummy;

import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.telemetry.ClientTelemetryPayload;
import org.apache.kafka.server.telemetry.ClientTelemetryReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopTelemetryReceiver implements ClientTelemetryReceiver, AutoCloseable {
  
  private static final Logger log = LoggerFactory.getLogger(NoopTelemetryReceiver.class);

  @Override
  public void exportMetrics(AuthorizableRequestContext context, ClientTelemetryPayload payload)  {
    System.out.println(payload.clientInstanceId());
  }


  @Override
  public void close() {
  }

}
