package io.kineticedge.kgr.clusters;


import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class SaslPlaintextOAuthKafkaContainer extends DynamicKafkaContainer {

    static final Network NETWORK = Network.newNetwork();

    static final KeycloakContainer keycloak = new KeycloakContainer()
            .withNetwork(NETWORK)
            .withNetworkAliases("keycloak")
            //  .withRealmImportFile("keycloak-realms.json")
            .withAdminUsername("admin")
            .withAdminPassword("keycloak");

    private static void setupKeycloakConfiguration() {

        try {
            // Give Keycloak time to fully start
            Thread.sleep(1000);

            String setupScript = readResource("keycloak-setup.sh");

            // Execute script in container
            var result = keycloak.execInContainer("/bin/sh", "-c", setupScript);

            System.out.println("Exit code: " + result.getExitCode());
            System.out.println("STDOUT:\n" + result.getStdout());
            System.out.println("STDERR:\n" + result.getStderr());

            if (result.getExitCode() != 0) {
                throw new RuntimeException("Setup script failed with exit code " + result.getExitCode());
            }

            System.out.println("Keycloak setup completed!");
        } catch (Exception e) {
            throw new RuntimeException("Failed to run Keycloak setup script", e);
        }
    }

    private static void setupKeycloakConfigurationold() {
        try {
            Keycloak adminClient = keycloak.getKeycloakAdminClient();
            var realm = adminClient.realm("master");

            // Import client scopes first (clients may reference them)
            List<ClientScopeRepresentation> clientScopes = loadClientScopes();
            for (ClientScopeRepresentation scope : clientScopes) {
                try {
                    realm.clientScopes().create(scope);
                } catch (Exception e) {
                    // Scope might already exist, ignore
                    System.out.println("Scope creation: " + e.getMessage());
                }
            }

            // Import clients
            List<ClientRepresentation> clients = loadClients();
            for (ClientRepresentation client : clients) {
                realm.clients().create(client);
            }

            System.out.println("Keycloak configuration imported successfully!");
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup Keycloak configuration", e);
        }
    }

    private static List<ClientRepresentation> loadClients() throws Exception {
        String json = readResource("keycloak-clients.json");
        ObjectMapper mapper = new ObjectMapper();
        return Arrays.asList(mapper.readValue(json, ClientRepresentation[].class));
    }

    private static List<ClientScopeRepresentation> loadClientScopes() throws Exception {
        String json = readResource("keycloak-clients-scopes.json");
        ObjectMapper mapper = new ObjectMapper();
        return Arrays.asList(mapper.readValue(json, ClientScopeRepresentation[].class));
    }

    public static String getTokenEndpoint() {
        return keycloak.getAuthServerUrl() + "realms/master/protocol/openid-connect/token";
    }

    public static String getJwksEndpoint() {
        return keycloak.getAuthServerUrl() + "realms/master/protocol/openid-connect/certs";
    }

    private static String readResource(String resourceName) throws Exception {
        try (var inputStream = ClassLoader.getSystemResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            return new String(inputStream.readAllBytes());
        }
    }

//    private final GenericContainer<?> oauth =
//            new GenericContainer<>(DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:latest"))
//                    .dependsOn(keycloak)
//                    .withNetwork(getNetwork())
//                    .withAccessToHost(true)
//                    .withNetworkAliases("oauth")
//                    .withEnv("MOCK_OAUTH2_SERVER_CONFIG_PATH", "/mock-oauth2.json")
//                    .withExposedPorts(8080)
//                    .withCopyToContainer(Transferable.of(OAUTH_CONFIG, RWRR), "/mock-oauth2.json");

    private static final String BROKER_JAAS_CONF = """
            external_sasl.KafkaServer {
              org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;
            };
            """;


    public SaslPlaintextOAuthKafkaContainer() {
        super("EXTERNAL_SASL");

//        copyFileToContainer(Transferable.of(BROKER_JAAS_CONF, RWRR), "/tmp/broker_jaas.conf");
//                KAFKA_LISTENERS: EXTERNAL_SASL://0.0.0.0:9092,BROKER://0.0.0.0:9093,CONTROLLER://0.0.0.0:9094
//        KAFKA_ADVERTISED_LISTENERS: EXTERNAL_SASL://localhost:9092,BROKER://kafka-broker:9093,CONTROLLER://kafka-broker:9094

        this
                .withNetwork(NETWORK)
                .withEnv(Map.ofEntries(
                        Map.entry("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "EXTERNAL_SASL:SASL_PLAINTEXT,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT"),
                        Map.entry("KAFKA_SASL_ENABLED_MECHANISMS", "OAUTHBEARER"),
                        Map.entry("KAFKA_LISTENER_NAME_EXTERNAL__SASL_SASL_ENABLED_MECHANISMS", "OAUTHBEARER"),

                        Map.entry("KAFKA_SASL_OAUTHBEARER_JWKS_ENDPOINT_URL", "http://keycloak:8080/realms/master/protocol/openid-connect/certs"),

                        Map.entry("KAFKA_SASL_OAUTHBEARER_SCOPE_CLAIM", "scope"),
                        Map.entry("KAFKA_SASL_OAUTHBEARER_EXPECTED_AUDIENCE", "kafka"),
                        //Map.entry("KAFKA_SASL_OAUTHBEARER_EXPECTED_ISSUER", "http://localhost:61369/realms/master"),
                        Map.entry("KAFKA_LISTENER_NAME_EXTERNAL__SASL_OAUTHBEARER_SUB_CLAIM_NAME", "sub"),

                        Map.entry("KAFKA_LISTENER_NAME_EXTERNAL__SASL_OAUTHBEARER_SASL_SERVER_CALLBACK_HANDLER_CLASS", "org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallbackHandler"),
                        Map.entry("KAFKA_LISTENER_NAME_EXTERNAL__SASL_SASL_OAUTHBEARER_JWT_VALIDATOR_CLASS", "org.apache.kafka.common.security.oauthbearer.BrokerJwtValidator"),

                        Map.entry("KAFKA_SASL_OAUTHBEARER_PRINCIPAL_CLAIM_NAME", "client_id"),

                        Map.entry("KAFKA_OPTS", "-Djava.security.auth.login.config=/tmp/broker_jaas.conf -Dorg.apache.kafka.sasl.oauthbearer.allowed.urls=http://keycloak:8080/realms/master/protocol/openid-connect/certs,http://keycloak:8080/realms/master/protocol/openid-connect/token,http://localhost:8080/realms/master/protocol/openid-connect/certs,http://localhost:8080/realms/master/protocol/openid-connect/token")
                ));
    }

    @Override
    public String clusterId() {
        return "SASLPLTXT-00000--00000";
    }

    @Override
    public Map<String, Object> connectionProperties() {
        System.out.println("Token URL: " + keycloak.getAuthServerUrl());

        return Map.ofEntries(
                Map.entry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers()),
                Map.entry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT"),
                Map.entry(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER"),

                Map.entry("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required clientId=\"app-ui\" clientSecret=\"app-ui-secret\" ;"),

                Map.entry("sasl.login.callback.handler.class", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler"),

                Map.entry(SaslConfigs.SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL, keycloak.getAuthServerUrl() + "/realms/master/protocol/openid-connect/token"),
                Map.entry("sasl.oauthbearer.jwt.retriever.class", "org.apache.kafka.common.security.oauthbearer.ClientCredentialsJwtRetriever")

//                Map.entry("sasl.oauthbearer.client.credentials.client.id", "oauth_user"),
//                Map.entry("sasl.oauthbearer.client.credentials.client.secret", "oauth_secret"),
//                Map.entry("sasl.oauthbearer.scope", "kafka")

        );
    }



    @Override
    public void start() {
        keycloak.start();
        setupKeycloakConfiguration();

        System.setProperty("org.apache.kafka.sasl.oauthbearer.allowed.urls", keycloak.getAuthServerUrl() + "/realms/master/protocol/openid-connect/certs, " + keycloak.getAuthServerUrl() + "/realms/master/protocol/openid-connect/token");

        super.start();

    }

    public void whileStarting() {
        copyFileToContainer(Transferable.of(BROKER_JAAS_CONF, RWRR), "/tmp/broker_jaas.conf");

    }
}