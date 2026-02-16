package io.kineticedge.kgr.clusters;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

public abstract class DynamicKafkaContainer extends GenericContainer<DynamicKafkaContainer> {


    protected static final int RWXRWXRWX = 0777;
    protected static final int RWRR = 0644;

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("apache/kafka:4.1.1");
    private static final DockerImageName APACHE_KAFKA_NATIVE_IMAGE_NAME = DockerImageName.parse("apache/kafka-native");
    private static final int KAFKA_PORT = 9092;
    private static final String DEFAULT_INTERNAL_TOPIC_RF = "1";
    private static final String STARTER_SCRIPT = "/tmp/testcontainers_start.sh";
    private static final String[] COMMAND = {"sh", "-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT,};
    private static final WaitStrategy WAIT_STRATEGY = Wait.forLogMessage(".*Transitioning from RECOVERY to RUNNING.*", 1);

    private static final String DOCKER_RUN = """
            #!/bin/bash
            export KAFKA_ADVERTISED_LISTENERS=%s://%s:%s,BROKER://localhost:9093,CONTROLLER://localhost:9094
            /etc/kafka/docker/run
            """;

    protected final Network network = Network.newNetwork();

    private final String listenerName;

    public DynamicKafkaContainer(String listenerName) {
        this(DEFAULT_IMAGE_NAME, listenerName);
    }

    public DynamicKafkaContainer(DockerImageName dockerImageName, String listenerName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME, APACHE_KAFKA_NATIVE_IMAGE_NAME);

        this.listenerName = listenerName;
        this
                .withExposedPorts(KAFKA_PORT)
                .withNetwork(network)
                .withEnv(Map.ofEntries(
                        Map.entry("CLUSTER_ID", clusterId()),
                        Map.entry("KAFKA_LISTENERS", listenerName + "://0.0.0.0:" + KAFKA_PORT + ",BROKER://0.0.0.0:9093,CONTROLLER://0.0.0.0:9094"),
                        Map.entry("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER"),
                        Map.entry("KAFKA_PROCESS_ROLES", "broker,controller"),
                        Map.entry("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER"),
                        Map.entry("KAFKA_NODE_ID", "1"),
                        Map.entry("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9094"),
                        Map.entry("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", DEFAULT_INTERNAL_TOPIC_RF),
                        Map.entry("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", DEFAULT_INTERNAL_TOPIC_RF),
                        Map.entry("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", DEFAULT_INTERNAL_TOPIC_RF),
                        Map.entry("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", DEFAULT_INTERNAL_TOPIC_RF),
                        Map.entry("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", Long.MAX_VALUE + ""),
                        Map.entry("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                )).withCommand(COMMAND)
                .waitingFor(WAIT_STRATEGY);
    }

    @Override
    protected void configure() {
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {

//        // IMPORTANT: During containerIsStarting(), prefer reading the mapped port from containerInfo,
//        // instead of calling getMappedPort(..), which may not be available yet.
//        ExposedPort exposed = ExposedPort.tcp(KAFKA_PORT);
//        Ports.Binding[] bindings = containerInfo.getNetworkSettings().getPorts().getBindings().get(exposed);
//        if (bindings == null || bindings.length == 0 || bindings[0] == null || bindings[0].getHostPortSpec() == null) {
//            throw new IllegalStateException("Port binding for " + exposed + " not available during containerIsStarting()");
//        }
//        int hostPort = Integer.parseInt(bindings[0].getHostPortSpec());


        whileStarting();

        copyFileToContainer(Transferable.of(String.format(DOCKER_RUN, listenerName, getHost(), getMappedPort(KAFKA_PORT)), RWXRWXRWX), STARTER_SCRIPT);
    }

    public String getBootstrapServers() {
        return String.format("%s:%s", getHost(), getMappedPort(KAFKA_PORT));
    }

    public void whileStarting() {
    }

    // by making each cluster type create its own ID, it will help with debugging.
    public abstract String clusterId();

    public abstract Map<String, Object> connectionProperties();

    @Override
    public void stop() {
        super.stop();
        logger().debug(getLogs());
    }

    protected Admin createAdmin() {
        final Map<String, Object> config = new HashMap<>(connectionProperties());
        return AdminClient.create(config);
    }

}