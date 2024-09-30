package org.taulin.component.impl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.taulin.component.EventFilterRunner;
import org.taulin.model.RecentChangeEvent;
import org.taulin.serialization.serializer.avro.RecentChangeEventKeySerializer;

import java.time.Duration;

@Slf4j
public class EventFilterRunnerImpl implements EventFilterRunner {
    private static final String WIKIMEDIA_SOURCE_NAME = "Wikimedia Recent Change Events";
    private static final String DEFAULT_RESTART_STRATEGY = "fixed-delay";

    private final String bootstrapServers;
    private final String groupId;
    private final String sourceTopicName;
    private final String schemaRegistryUrl;
    private final String schemaRegistrySubject;
    private final String sinkTopicName;
    private final StreamExecutionEnvironment env;
    private final FilterFunction<RecentChangeEvent> eventsFilterFunction;

    @Inject
    public EventFilterRunnerImpl(
            @Named("bootstrap.servers") String bootstrapServers,
            @Named("group.id") String groupId,
            @Named("source.topic.name") String sourceTopicName,
            @Named("schema.registry.url") String schemaRegistryUrl,
            @Named("schema.registry.subject") String schemaRegistrySubject,
            @Named("sink.topic.name") String sinkTopicName,
            FilterFunction<RecentChangeEvent> eventsFilterFunction
    ) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.sourceTopicName = sourceTopicName;
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.schemaRegistrySubject = schemaRegistrySubject;
        this.sinkTopicName = sinkTopicName;

        Configuration config = new Configuration();
        config.set(RestartStrategyOptions.RESTART_STRATEGY, DEFAULT_RESTART_STRATEGY);
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, 5);
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ofMinutes(1));
        env = StreamExecutionEnvironment.getExecutionEnvironment(config);

        this.eventsFilterFunction = eventsFilterFunction;
    }

    @Override
    public void run() {
        try {
            KafkaSource<RecentChangeEvent> wikimediaEventsSource = KafkaSource.<RecentChangeEvent>builder()
                    .setBootstrapServers(bootstrapServers)
                    .setTopics(sourceTopicName)
                    .setGroupId(groupId)
                    .setStartingOffsets(OffsetsInitializer.earliest())
                    .setValueOnlyDeserializer(ConfluentRegistryAvroDeserializationSchema
                            .forSpecific(RecentChangeEvent.class, schemaRegistryUrl))
                    .build();

            DataStream<RecentChangeEvent> eventsDataStream = env.fromSource(wikimediaEventsSource,
                    WatermarkStrategy.noWatermarks(),
                    WIKIMEDIA_SOURCE_NAME);

            Sink<RecentChangeEvent> filteredEventsSink = KafkaSink.<RecentChangeEvent>builder()
                    .setBootstrapServers(bootstrapServers)
                    .setRecordSerializer(KafkaRecordSerializationSchema.<RecentChangeEvent>builder()
                            .setTopic(sinkTopicName)
                            .setKeySerializationSchema(new RecentChangeEventKeySerializer())
                            .setValueSerializationSchema(ConfluentRegistryAvroSerializationSchema
                                    .forSpecific(RecentChangeEvent.class, schemaRegistrySubject, schemaRegistryUrl))
                            .build()
                    )
                    .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                    .build();

            eventsDataStream
                    .filter(eventsFilterFunction)
                    .sinkTo(filteredEventsSink);

            env.executeAsync();
        } catch (Exception e) {
            log.error("Failed to run filter in data stream", e);
        }
    }

    @Override
    public void close() throws Exception {
        env.close();
    }
}
