package si.result.project.eearly.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.exception.DomainException;

public final class JsonColumnUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonColumnUtils() {
    }

    public static <T> List<T> readList(final String json, final TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new DomainException(
                    DomainExceptionCode.ALGORITHM_EXECUTION_INVALID_INPUT_PARAMETERS,
                    exception.getOriginalMessage()
            );
        }
    }

    public static String write(final Object value) {
        if (value == null) {
            return null;
        }

        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException(
                    DomainExceptionCode.ALGORITHM_EXECUTION_INVALID_INPUT_PARAMETERS,
                    exception.getOriginalMessage()
            );
        }
    }
}
