package org.taulin.component.impl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.taulin.serialization.deserializer.avro.RecentChangeEventAvroDeserializer;
import org.taulin.component.EventFilterRunner;
import org.taulin.model.RecentChangeEvent;
import org.taulin.serialization.serializer.avro.RecentChangeEventAvroSerializer;
import org.taulin.serialization.serializer.avro.RecentChangeEventKeySerializer;

import java.util.List;

@Slf4j
public class EventFilterRunnerImpl implements EventFilterRunner {
    private static final String WIKIMEDIA_SOURCE_NAME = "Wikimedia Recent Change Events";

    private final String bootstrapServers;
    private final String groupId;
    private final String sourceTopicName;
    private final String sinkTopicName;
    private final StreamExecutionEnvironment env;
    private final List<String> filterTitleUrls;

    @Inject
    public EventFilterRunnerImpl(
            @Named("bootstrap.servers") String bootstrapServers,
            @Named("group.id") String groupId,
            @Named("source.topic.name") String sourceTopicName,
            @Named("sink.topic.name") String sinkTopicName,
            @Named("filter.title.urls") String filterTitleUrlsStr
    ) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.sourceTopicName = sourceTopicName;
        this.sinkTopicName = sinkTopicName;
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        filterTitleUrls = List.of(filterTitleUrlsStr.split(","));
    }

    @Override
    public void run() {
        try {
            KafkaSource<RecentChangeEvent> wikimediaEventsSource = KafkaSource.<RecentChangeEvent>builder()
                    .setBootstrapServers(bootstrapServers)
                    .setTopics(sourceTopicName)
                    .setGroupId(groupId)
                    .setStartingOffsets(OffsetsInitializer.earliest())
                    .setValueOnlyDeserializer(new RecentChangeEventAvroDeserializer())
                    .build();

            DataStream<RecentChangeEvent> eventsDataStream = env.fromSource(wikimediaEventsSource,
                    WatermarkStrategy.noWatermarks(),
                    WIKIMEDIA_SOURCE_NAME);

            Sink<RecentChangeEvent> filteredEventsSink = KafkaSink.<RecentChangeEvent>builder()
                    .setBootstrapServers(bootstrapServers)
                    .setRecordSerializer(KafkaRecordSerializationSchema.<RecentChangeEvent>builder()
                            .setTopic(sinkTopicName)
                            .setKeySerializationSchema(new RecentChangeEventKeySerializer())
                            .setValueSerializationSchema(new RecentChangeEventAvroSerializer())
                            .build()
                    )
                    .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                    .build();

            eventsDataStream
                    .filter((event) -> filterTitleUrls.contains(event.getTitleUrl().toString()))
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
