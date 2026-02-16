package io.kineticedge.kgr.clusters;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.testcontainers.utility.MountableFile;

import java.util.Map;

public final class SslKafkaContainer extends DynamicKafkaContainer {

    public SslKafkaContainer() {
        super("EXTERNAL_SSL");

        this
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("kafka.keystore.jks"),
                        "/etc/kafka/secrets/kafka.keystore.jks"
                )
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("kafka.server.truststore.jks"),
                        "/etc/kafka/secrets/kafka.server.truststore.jks"
                )
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("kafka.key"),
                        "/etc/kafka/secrets/kafka.key"
                )
                .withEnv(Map.ofEntries(
                        Map.entry("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                                "EXTERNAL_SSL:SSL,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT"),

                        Map.entry("KAFKA_SSL_CLIENT_AUTH", "required"),

                        Map.entry("KAFKA_SSL_KEYSTORE_FILENAME", "kafka.keystore.jks"),
                        Map.entry("KAFKA_SSL_KEY_CREDENTIALS", "kafka.key"),
                        Map.entry("KAFKA_SSL_KEYSTORE_CREDENTIALS", "kafka.key"),
                        Map.entry("KAFKA_SSL_TRUSTSTORE_FILENAME", "kafka.server.truststore.jks"),
                        Map.entry("KAFKA_SSL_TRUSTSTORE_CREDENTIALS", "kafka.key")

                        //Map.entry("KAFKA_OPTS", "-Djavax.net.debug=ssl:trustmanager,session,handshake:verbose")
                ));
    }

    @Override
    public String clusterId() {
        return "SSL-00000--00000";
    }

    @Override
    public Map<String, Object> connectionProperties() {

        String path = Thread.currentThread().getContextClassLoader().getResource("kafka.server.truststore.jks").getPath();
        String clientKeystore = Thread.currentThread().getContextClassLoader().getResource("client.keystore.jks").getPath();
        return Map.ofEntries(
                Map.entry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers()),
                Map.entry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL"),

                Map.entry(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, path),
                Map.entry(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "broker_secret"),

                Map.entry(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, clientKeystore),
                Map.entry(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "broker_secret"),
                Map.entry(SslConfigs.SSL_KEY_PASSWORD_CONFIG, "broker_secret")
        );
    }

}