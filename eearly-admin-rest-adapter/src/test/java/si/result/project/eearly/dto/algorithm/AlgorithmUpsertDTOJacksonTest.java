package si.result.project.eearly.dto.algorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlgorithmUpsertDTOJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeInputSchemaFromJsonObject() throws Exception {
        final String body = """
                {
                  "id": "%s",
                  "inputSchema": {
                    "input": {
                      "type": "number"
                    }
                  },
                  "outputSchema": {
                    "result": true
                  }
                }
                """.formatted(UUID.randomUUID());

        final AlgorithmUpsertDTO dto = objectMapper.readValue(body, AlgorithmUpsertDTO.class);

        assertEquals("{\"input\":{\"type\":\"number\"}}", dto.inputSchema());
        assertEquals("{\"result\":true}", dto.outputSchema());
    }

    @Test
    void shouldDeserializeInputSchemaFromJsonString() throws Exception {
        final String body = """
                {
                  "id": "%s",
                  "inputSchema": "{\\"input\\":{\\"type\\":\\"number\\"}}"
                }
                """.formatted(UUID.randomUUID());

        final AlgorithmUpsertDTO dto = objectMapper.readValue(body, AlgorithmUpsertDTO.class);

        assertEquals("{\"input\":{\"type\":\"number\"}}", dto.inputSchema());
    }
}
