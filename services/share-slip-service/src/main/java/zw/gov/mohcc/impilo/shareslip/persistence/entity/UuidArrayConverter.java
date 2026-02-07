package zw.gov.mohcc.impilo.shareslip.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * JPA AttributeConverter for PostgreSQL UUID[] column type.
 *
 * Converts between Java {@code List<UUID>} and PostgreSQL {@code uuid[]} arrays.
 * This converter handles the JDBC Array interface used by PostgreSQL for array columns.
 */
@Converter
public class UuidArrayConverter implements AttributeConverter<List<UUID>, Object> {

    @Override
    public Object convertToDatabaseColumn(List<UUID> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return new UUID[0];
        }
        return attribute.toArray(new UUID[0]);
    }

    @Override
    public List<UUID> convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return new ArrayList<>();
        }
        if (dbData instanceof Array sqlArray) {
            try {
                UUID[] uuids = (UUID[]) sqlArray.getArray();
                return new ArrayList<>(Arrays.asList(uuids));
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert UUID[] from database", e);
            }
        }
        if (dbData instanceof UUID[] uuids) {
            return new ArrayList<>(Arrays.asList(uuids));
        }
        throw new IllegalArgumentException("Unsupported database column type for UUID[]: " + dbData.getClass());
    }
}
