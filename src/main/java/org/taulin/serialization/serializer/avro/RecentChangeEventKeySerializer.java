package org.taulin.serialization.serializer.avro;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.taulin.model.RecentChangeEvent;

import java.util.Objects;

public class RecentChangeEventKeySerializer implements SerializationSchema<RecentChangeEvent> {
    @Override
    public byte[] serialize(RecentChangeEvent recentChangeEvent) {
        return Objects.isNull(recentChangeEvent.getId())
                ? null
                : serializeLong(recentChangeEvent.getId());
    }

    private byte[] serializeLong(Long data) {
        if (data == null)
            return null;

        return new byte[]{
                (byte) (data >>> 56),
                (byte) (data >>> 48),
                (byte) (data >>> 40),
                (byte) (data >>> 32),
                (byte) (data >>> 24),
                (byte) (data >>> 16),
                (byte) (data >>> 8),
                data.byteValue()
        };
    }
}
