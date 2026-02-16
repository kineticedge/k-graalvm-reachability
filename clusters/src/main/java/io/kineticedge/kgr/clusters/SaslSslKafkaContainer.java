package io.kineticedge.kgr.clusters;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.util.Map;

public final class SaslSslKafkaContainer extends DynamicKafkaContainer {


    private static final String BROKER_JAAS_CONF = """
    external_sasl_ssl.KafkaServer {
        org.apache.kafka.common.security.plain.PlainLoginModule required user_foo="bar";
        org.apache.kafka.common.security.scram.ScramLoginModule required;
    };
    """;

    public SaslSslKafkaContainer() {
        super("EXTERNAL_SASL_SSL");

//        copyFileToContainer(Transferable.of(BROKER_JAAS_CONF, RWRR), "/tmp/broker_jaas.conf");

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

                        Map.entry("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "EXTERNAL_SASL_SSL:SASL_SSL,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT"),
                        Map.entry("KAFKA_SASL_ENABLED_MECHANISMS", "PLAIN,SCRAM-SHA-256,SCRAM-SHA-512"),
                        Map.entry("KAFKA_LISTENER_NAME_EXTERNAL__SASL_SASL_ENABLED_MECHANISMS", "PLAIN,SCRAM-SHA-256,SCRAM-SHA-512"),
                        Map.entry("KAFKA_OPTS", "-Djava.security.auth.login.config=/tmp/broker_jaas.conf"),


                        Map.entry("KAFKA_SSL_CLIENT_AUTH", "none"),

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
        return "SASL-SSL-00000--00000";
    }

    @Override
    public Map<String, Object> connectionProperties() {

        String path = Thread.currentThread().getContextClassLoader().getResource("kafka.server.truststore.jks").getPath();
        String clientKeystore = Thread.currentThread().getContextClassLoader().getResource("client.keystore.jks").getPath();
        return Map.ofEntries(
                Map.entry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers()),
                Map.entry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL"),

                Map.entry(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, path),
                Map.entry(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "broker_secret"),

                Map.entry(SaslConfigs.SASL_MECHANISM, "PLAIN"),
                Map.entry(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"foo\" password=\"bar\";")

//                Map.entry(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, clientKeystore),
//                Map.entry(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "broker_secret"),
//                Map.entry(SslConfigs.SSL_KEY_PASSWORD_CONFIG, "broker_secret")
        );
    }

    public void whileStarting() {
      copyFileToContainer(Transferable.of(BROKER_JAAS_CONF, RWRR), "/tmp/broker_jaas.conf");
    }

}