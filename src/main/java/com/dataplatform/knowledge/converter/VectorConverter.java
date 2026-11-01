package com.dataplatform.knowledge.converter;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter: float[] <-> PGvector
 * Task 4 - Step 3
 */
@Converter
public class VectorConverter implements AttributeConverter<float[], PGvector> {

    @Override
    public PGvector convertToDatabaseColumn(float[] attribute) {
        return attribute == null ? null : new PGvector(attribute);
    }

    @Override
    public float[] convertToEntityAttribute(PGvector dbData) {
        return dbData == null ? null : dbData.toArray();
    }
}
