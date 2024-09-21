package org.taulin.serialization.serializer.avro;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.kafka.common.errors.SerializationException;
import org.taulin.model.RecentChangeEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
public class RecentChangeEventAvroSerializer implements SerializationSchema<RecentChangeEvent> {
    @Override
    public byte[] serialize(RecentChangeEvent event) {
        try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Encoder binaryEncoder = EncoderFactory.get().binaryEncoder(output, null);

            GenericDatumWriter<RecentChangeEvent> writer = new GenericDatumWriter<>(event.getSchema());
            writer.write(event, binaryEncoder);

            binaryEncoder.flush();

            return output.toByteArray();
        } catch (IOException e) {
            log.error("Error deserializing event: {}", event, e);
            throw new SerializationException(e);
        }
    }
}
