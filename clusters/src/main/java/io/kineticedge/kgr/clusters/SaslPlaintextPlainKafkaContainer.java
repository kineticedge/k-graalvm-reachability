package io.kineticedge.kgr.clusters;


import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.common.config.SaslConfigs;
import org.slf4j.Logger;
import org.testcontainers.images.builder.Transferable;

import java.util.List;
import java.util.Map;

public final class SaslPlaintextPlainKafkaContainer extends DynamicKafkaContainer {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SaslPlaintextPlainKafkaContainer.class);

    private static final String BROKER_JAAS_CONF = """
            external_sasl.KafkaServer {
                org.apache.kafka.common.security.plain.PlainLoginModule required user_foo="bar";
                org.apache.kafka.common.security.scram.ScramLoginModule required;
            };
            """;

    public SaslPlaintextPlainKafkaContainer() {
        super("EXTERNAL_SASL");

//        copyFileToContainer(Transferable.of(BROKER_JAAS_CONF, RWRR), "/tmp/broker_jaas.conf");

        this.withEnv(Map.of(
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "EXTERNAL_SASL:SASL_PLAINTEXT,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT",
                "KAFKA_SASL_ENABLED_MECHANISMS", "PLAIN,SCRAM-SHA-256,SCRAM-SHA-512",
                "KAFKA_LISTENER_NAME_EXTERNAL__SASL_SASL_ENABLED_MECHANISMS", "PLAIN,SCRAM-SHA-256,SCRAM-SHA-512",
                "KAFKA_OPTS", "-Djava.security.auth.login.config=/tmp/broker_jaas.conf"
        ));
    }

    @Override
    public String clusterId() {
        return "SASLPLTXT-00000--00000";
    }


    @Override
    public void whileStarting() {
        copyFileToContainer(Transferable.of(BROKER_JAAS_CONF, RWRR), "/tmp/broker_jaas.conf");
    }

    @Override
    public Map<String, Object> connectionProperties() {
        return Map.ofEntries(
                Map.entry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers()),
                Map.entry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT"),
                Map.entry(SaslConfigs.SASL_MECHANISM, "PLAIN"),
                Map.entry(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"foo\" password=\"bar\";")
        );
    }

    public void createScramUser(ScramMechanism mechanism, String username, String password) {
        try (Admin adminClient = createAdmin()) {
            try {
                adminClient
                        .alterUserScramCredentials(
                                List.of(
                                        new UserScramCredentialUpsertion(
                                                username,
                                                new ScramCredentialInfo(mechanism, 4096), password)
                                )
                        )
                        .all()
                        .get();
                log.debug("created SCRAM user {} with mechanism {}", username, mechanism);
            } catch (Exception e) {
                throw new RuntimeException("failed to create scram user", e);
            }
        }
    }
}