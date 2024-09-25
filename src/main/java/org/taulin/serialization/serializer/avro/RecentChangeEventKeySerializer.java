package org.taulin.serialization.serializer.avro;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.kafka.common.serialization.LongSerializer;
import org.taulin.model.RecentChangeEvent;

import java.util.Objects;

public class RecentChangeEventKeySerializer implements SerializationSchema<RecentChangeEvent> {
    private final LongSerializer serializer = new LongSerializer();

    @Override
    public byte[] serialize(RecentChangeEvent recentChangeEvent) {
        return Objects.isNull(recentChangeEvent.getId())
                ? null
                : serializer.serialize(null, recentChangeEvent.getId());
    }
}
