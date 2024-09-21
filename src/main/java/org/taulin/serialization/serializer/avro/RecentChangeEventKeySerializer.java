package org.taulin.serialization.serializer.avro;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.taulin.model.RecentChangeEvent;

public class RecentChangeEventKeySerializer implements SerializationSchema<RecentChangeEvent> {
    @Override
    public byte[] serialize(RecentChangeEvent recentChangeEvent) {
        return recentChangeEvent.getId().toString().getBytes();
    }
}
