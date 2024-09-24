package org.taulin.serialization.serializer.avro;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.taulin.model.RecentChangeEvent;

import java.util.Objects;

public class RecentChangeEventKeySerializer implements SerializationSchema<RecentChangeEvent> {
    @Override
    public byte[] serialize(RecentChangeEvent recentChangeEvent) {
        return Objects.isNull(recentChangeEvent.getId())
                ? null
                : recentChangeEvent.getId().toString().getBytes();
    }
}
