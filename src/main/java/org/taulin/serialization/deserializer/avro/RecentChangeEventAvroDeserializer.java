package org.taulin.serialization.deserializer.avro;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.kafka.common.errors.SerializationException;
import org.taulin.model.RecentChangeEvent;

import java.io.ByteArrayInputStream;

@Slf4j
public class RecentChangeEventAvroDeserializer implements DeserializationSchema<RecentChangeEvent> {
    @Override
    public RecentChangeEvent deserialize(byte[] data) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data)) {
            Decoder binaryDecoder = DecoderFactory.get().binaryDecoder(byteArrayInputStream, null);

            SpecificDatumReader<RecentChangeEvent> reader = new SpecificDatumReader<>(RecentChangeEvent.getClassSchema());
            return reader.read(null, binaryDecoder);
        } catch (Exception e) {
            log.error("Error deserializing event", e);
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean isEndOfStream(RecentChangeEvent recentChangeEvent) {
        return false;
    }

    @Override
    public TypeInformation<RecentChangeEvent> getProducedType() {
        return TypeInformation.of(RecentChangeEvent.class);
    }
}
