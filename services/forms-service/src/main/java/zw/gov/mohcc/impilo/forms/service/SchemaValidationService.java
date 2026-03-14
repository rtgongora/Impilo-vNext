package zw.gov.mohcc.impilo.forms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class SchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationService.class);

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public SchemaValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    /**
     * Validates a JSON payload string against a JSON Schema string.
     *
     * @param schemaJson the JSON Schema definition
     * @param payload    the JSON payload to validate
     * @return list of validation error messages; empty if valid
     */
    public List<String> validate(String schemaJson, String payload) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            JsonNode payloadNode = objectMapper.readTree(payload);

            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(payloadNode);

            if (errors.isEmpty()) {
                return Collections.emptyList();
            }

            return errors.stream()
                    .map(ValidationMessage::getMessage)
                    .toList();
        } catch (Exception e) {
            log.error("Schema validation failed due to parsing error", e);
            return List.of("Schema validation error: " + e.getMessage());
        }
    }
}
