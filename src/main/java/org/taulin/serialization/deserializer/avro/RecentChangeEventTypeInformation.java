package org.taulin.serialization.deserializer.avro;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.taulin.model.RecentChangeEvent;

public class RecentChangeEventTypeInformation extends TypeInformation<RecentChangeEvent> {
    @Override
    public boolean isBasicType() {
        return false;
    }

    @Override
    public boolean isTupleType() {
        return false;
    }

    @Override
    public int getArity() {
        return 0;
    }

    @Override
    public int getTotalFields() {
        return 0;
    }

    @Override
    public Class<RecentChangeEvent> getTypeClass() {
        return null;
    }

    @Override
    public boolean isKeyType() {
        return false;
    }

    @Override
    public TypeSerializer<RecentChangeEvent> createSerializer(ExecutionConfig executionConfig) {
        return null;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean canEqual(Object o) {
        return false;
    }
}
