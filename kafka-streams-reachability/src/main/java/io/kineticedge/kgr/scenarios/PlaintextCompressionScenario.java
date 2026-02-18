package io.kineticedge.kgr.scenarios;

import io.kineticedge.kgr.clusters.PlaintextKafkaContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.processor.LogAndSkipOnInvalidTimestamp;
import org.apache.kafka.streams.processor.StateRestoreCallback;
import org.apache.kafka.streams.processor.StateRestoreListener;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.processor.UsePartitionTimeOnInvalidTimestamp;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.apache.kafka.streams.processor.assignment.TaskAssignor;
import org.apache.kafka.streams.processor.assignment.assignors.StickyTaskAssignor;
import org.apache.kafka.streams.state.RocksDBConfigSetter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PlaintextCompressionScenario extends AbstractScenario<PlaintextKafkaContainer> {

    // we want to use standard main method, since non-standard requires some discovery
    // within the jvm which causes GraalVM to generate a record for it.
    public static void main(String[] args) {
        var scenario = new PlaintextCompressionScenario();
        scenario.start();
        scenario.scenario();
        scenario.stop();
    }

    private static final List<String> COMPRESSION_TYPES = List.of("none", "gzip", "snappy", "lz4", "zstd");

    private static final List<Class<? extends DeserializationExceptionHandler>> DESERIALIZATION_EXCEPTION_HANDLERS = List.of(
            LogAndFailExceptionHandler.class,
            LogAndContinueExceptionHandler.class
    );

    private static final List<Class<? extends ProductionExceptionHandler>> PRODUCTION_EXCEPTION_HANDLERS = List.of(
            DefaultProductionExceptionHandler.class
    );


    private static final List<Class<? extends TimestampExtractor>> TIMESTAMP_EXTRACTORS = List.of(
            FailOnInvalidTimestamp.class,
            LogAndSkipOnInvalidTimestamp.class,
            UsePartitionTimeOnInvalidTimestamp.class,
            WallclockTimestampExtractor.class
    );

    private static final List<Class<? extends TaskAssignor>> TASK_ASSIGNORS = List.of(
            StickyTaskAssignor.class
    );

    // only interfaces exist so there is no class in kafka-streams that should be included (at least for now)

    private static final List<Class<? extends StateRestoreListener>> STATE_RESTORE_LISTENERS = List.of(
            StateRestoreListener.class
    );
    //not public, KafkaStreams$DelegatingStateRestoreListener, not included but may need to re-evaluate if we see issues.

    private static final List<Class<? extends StateRestoreCallback>> STATE_RESTORE_CALLBACKS = List.of(
            StateRestoreCallback.class
    );

    private static final List<Class<? extends RocksDBConfigSetter>> ROCKSDB_CONFIG_SETTERS = List.of(
            RocksDBConfigSetter.class
    );

    private final AtomicInteger counter = new AtomicInteger(0);

    public PlaintextCompressionScenario() {
        super(new PlaintextKafkaContainer());
    }

    void scenario() {

        var topics = List.of("in", "out");

        createTopics(topics);

        COMPRESSION_TYPES.forEach(compression -> {
            try (KafkaProducer<String, String> producer = createProducer(compression)) {
                producer.send(new ProducerRecord<>("in", "bar", "value"));
            }
        });


        doLoop(StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, DESERIALIZATION_EXCEPTION_HANDLERS, topics);
        doLoop(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, TIMESTAMP_EXTRACTORS, topics);
        doLoop(StreamsConfig.PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, PRODUCTION_EXCEPTION_HANDLERS, topics);
        doLoop(StreamsConfig.TASK_ASSIGNOR_CLASS_CONFIG, TASK_ASSIGNORS, topics);

    }

    private <T extends Class<?>> void doLoop(final String config, final List<? extends T> classes, List<String> topics) {
        classes.forEach(c -> {
            var map = Map.<String, Object>ofEntries(
                    Map.entry(config, c.getName())
            );
            doRun(map, topics);
        });

    }

    private void doRun(Map<String, Object> map, List<String> topics) {
        try (var streams = createStreams(map, "appid-" + counter.incrementAndGet(), topics)) {
            streams.start();
            int count = 0;
            while (count < 20 && streams.state() != KafkaStreams.State.RUNNING) {
                count++;
                sleep(100L);
            }
            sleep(100L);
        }
    }

}
