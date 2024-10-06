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

import static org.taulin.util.JdbcPrimitivesUtil.*;

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
                        statement.setLong(1, handleLong(event.getId()));
                        statement.setString(2, handleCharSequence(event.getSchema$()));
                        statement.setString(3, handleCharSequence(event.getType()));
                        statement.setInt(4, handleInteger(event.getNamespace()));
                        statement.setString(5, handleCharSequence(event.getTitle()));
                        statement.setString(6, handleCharSequence(event.getTitleUrl()));
                        statement.setString(7, handleCharSequence(event.getComment()));
                        statement.setString(8, handleEpoch(event.getTimestamp()));
                        statement.setString(9, handleCharSequence(event.getUser()));
                        statement.setBoolean(10, handleBoolean(event.getBot()));
                        statement.setString(11, handleCharSequence(event.getNotifyUrl()));
                        statement.setString(12, handleCharSequence(event.getServerUrl()));
                        statement.setString(13, handleCharSequence(event.getServerName()));
                        statement.setString(14, handleCharSequence(event.getServerScriptPath()));
                        statement.setString(15, handleCharSequence(event.getWiki()));
                        statement.setString(16, handleCharSequence(event.getParsedComment()));
                        statement.setBoolean(17, handleBoolean(event.getMinor()));
                        statement.setBoolean(18, handleBoolean(event.getPatrolled()));
                        statement.setString(19, Objects.nonNull(event.getMeta())
                                ? handleCharSequence(event.getMeta().getUri())
                                : null);
                        statement.setString(20, Objects.nonNull(event.getMeta())
                                ? handleCharSequence(event.getMeta().getRequestId())
                                : null);
                        statement.setString(21, Objects.nonNull(event.getMeta())
                                ? handleCharSequence(event.getMeta().getId())
                                : null);
                        statement.setString(22, Objects.nonNull(event.getMeta())
                                ? handleCharSequence(event.getMeta().getDt())
                                : null);
                        statement.setString(23, Objects.nonNull(event.getMeta())
                                ? handleCharSequence(event.getMeta().getDomain())
                                : null);
                        statement.setString(24, Objects.nonNull(event.getMeta())
                                ? handleCharSequence(event.getMeta().getStream())
                                : null);
                        statement.setString(25, Objects.nonNull(event.getMeta())
                                ? handleCharSequence(event.getMeta().getTopic())
                                : null);
                        statement.setInt(26, Objects.nonNull(event.getMeta())
                                ? handleInteger(event.getMeta().getPartition())
                                : Integer.MIN_VALUE);
                        statement.setLong(27, Objects.nonNull(event.getMeta())
                                ? handleLong(event.getMeta().getOffset())
                                : Long.MIN_VALUE);
                        statement.setLong(28, Objects.nonNull(event.getLength())
                                ? handleLong(event.getLength().getOld())
                                : Long.MIN_VALUE);
                        statement.setLong(29, Objects.nonNull(event.getLength())
                                ? handleLong(event.getLength().getNew$())
                                : Long.MIN_VALUE);
                        statement.setLong(30, Objects.nonNull(event.getLength())
                                ? handleLong(event.getRevision().getOld())
                                : Long.MIN_VALUE);
                        statement.setLong(31, Objects.nonNull(event.getLength())
                                ? handleLong(event.getRevision().getNew$())
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
