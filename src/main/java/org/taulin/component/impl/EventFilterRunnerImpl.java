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
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.taulin.component.EventFilterRunner;
import org.taulin.model.RecentChangeEvent;
import org.taulin.serialization.serializer.avro.RecentChangeEventKeySerializer;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.Objects;

@Slf4j
public class EventFilterRunnerImpl implements EventFilterRunner {
    private static final String WIKIMEDIA_SOURCE_NAME = "Wikimedia Recent Change Events";
    private static final String DEFAULT_RESTART_STRATEGY = "fixed-delay";
    private static final String EVENT_INSERT_SCRIPT = """
            INSERT INTO recent_change_events."event" (
            	id,\s
             	"schema",\s
             	"type",\s
             	"namespace",\s
             	title,\s
             	title_url,\s
             	"comment",\s
             	"timestamp",\s
             	"user",\s
             	bot,\s
             	notify_url,\s
             	server_url,\s
             	server_name,\s
             	server_script_path,\s
             	wiki,\s
             	parsed_comment,\s
             	minor,\s
             	patrolled,\s
             	meta_uri,\s
             	meta_request_id,\s
             	meta_id,\s
             	meta_dt,\s
             	meta_domain,\s
             	meta_stream,\s
             	meta_topic,\s
             	meta_partition,\s
             	meta_offset,\s
             	length_old,\s
             	length_new,\s
             	revision_old,\s
             	revision_new
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;

    private final String bootstrapServers;
    private final String groupId;
    private final String sourceTopicName;
    private final String schemaRegistryUrl;
    private final String schemaRegistrySubject;
    private final String sinkTopicName;
    private final StreamExecutionEnvironment env;
    private final FilterFunction<RecentChangeEvent> eventsFilterFunction;
    private final JdbcConnectionOptions jdbcConnectionOptions;

    @Inject
    public EventFilterRunnerImpl(
            @Named("bootstrap.servers") String bootstrapServers,
            @Named("group.id") String groupId,
            @Named("source.topic.name") String sourceTopicName,
            @Named("schema.registry.url") String schemaRegistryUrl,
            @Named("schema.registry.subject") String schemaRegistrySubject,
            @Named("sink.topic.name") String sinkTopicName,
            FilterFunction<RecentChangeEvent> eventsFilterFunction,
            @Named("jdbc.driver.name") String jdbcDriverName,
            @Named("jdbc.datasource") String jdbcDatasource,
            @Named("jdbc.user") String jdbcUser,
            @Named("jdbc.password") String jdbcPassword
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
        this.jdbcConnectionOptions = new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl(jdbcDatasource)
                .withDriverName(jdbcDriverName)
                .withUsername(jdbcUser)
                .withPassword(jdbcPassword)
                .build();
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

            SinkFunction<RecentChangeEvent> eventsBackupSink = JdbcSink.sink(
                    EVENT_INSERT_SCRIPT,
                    (PreparedStatement statement, RecentChangeEvent event) -> {
                        statement.setLong(1, Objects.nonNull(event.getId())
                                ? event.getId()
                                : Long.MIN_VALUE);
                        statement.setString(2, event.getSchema$().toString());
                        statement.setString(3, event.getType().toString());
                        statement.setInt(4, Objects.nonNull(event.getNamespace())
                                ? event.getNamespace()
                                : Integer.MIN_VALUE);
                        statement.setString(5, event.getTitle().toString());
                        statement.setString(6, event.getTitleUrl().toString());
                        statement.setString(7, event.getComment().toString());
                        statement.setLong(8, Objects.nonNull(event.getTimestamp())
                                ? event.getTimestamp()
                                : Long.MIN_VALUE);
                        statement.setString(9, event.getUser().toString());
                        statement.setBoolean(10, Boolean.TRUE.equals(event.getBot()));
                        statement.setString(11, event.getNotifyUrl().toString());
                        statement.setString(12, event.getServerUrl().toString());
                        statement.setString(13, event.getServerName().toString());
                        statement.setString(14, event.getServerScriptPath().toString());
                        statement.setString(15, event.getWiki().toString());
                        statement.setString(16, event.getParsedComment().toString());
                        statement.setBoolean(17, Boolean.TRUE.equals(event.getMinor()));
                        statement.setBoolean(18, Boolean.TRUE.equals(event.getPatrolled()));
                        statement.setString(19, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getUri().toString()
                                : null);
                        statement.setString(20, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getRequestId().toString()
                                : null);
                        statement.setString(21, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getId().toString()
                                : null);
                        statement.setString(22, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getDt().toString()
                                : null);
                        statement.setString(23, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getDomain().toString()
                                : null);
                        statement.setString(24, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getStream().toString()
                                : null);
                        statement.setString(25, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getTopic().toString()
                                : null);
                        statement.setInt(26, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getPartition()
                                : Integer.MAX_VALUE);
                        statement.setLong(27, Objects.nonNull(event.getMeta())
                                ? event.getMeta().getOffset()
                                : Long.MIN_VALUE);
                        statement.setLong(28, Objects.nonNull(event.getLength())
                                ? event.getLength().getOld()
                                : Long.MIN_VALUE);
                        statement.setLong(29, Objects.nonNull(event.getLength())
                                ? event.getLength().getNew$()
                                : Long.MIN_VALUE);
                        statement.setLong(30, Objects.nonNull(event.getLength())
                                ? event.getRevision().getOld()
                                : Long.MIN_VALUE);
                        statement.setLong(31, Objects.nonNull(event.getLength())
                                ? event.getRevision().getNew$()
                                : Long.MIN_VALUE);
                    },
                    JdbcExecutionOptions.builder()
                            .withBatchSize(1000)
                            .withBatchIntervalMs(200)
                            .withMaxRetries(5)
                            .build(),
                    jdbcConnectionOptions
            );

            eventsDataStream.addSink(eventsBackupSink);

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
