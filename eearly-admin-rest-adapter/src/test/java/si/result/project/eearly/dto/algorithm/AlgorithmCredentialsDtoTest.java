package si.result.project.eearly.dto.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import si.result.project.eearly.mapper.algorithm.AlgorithmMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.command.CreateAlgorithmCommand;
import si.result.project.eearly.model.algorithm.command.UpdateAlgorithmCommand;

/** The password of an algorithm goes in, never out. */
class AlgorithmCredentialsDtoTest {

    private static final String PASSWORD = "plain-" + UUID.randomUUID();
    private static final String CIPHERTEXT = "v1:" + UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AlgorithmMapper mapper = Mappers.getMapper(AlgorithmMapper.class);

    private AlgorithmUpsertDTO upsert(final String username, final String password, final Boolean hasPassword) {
        return new AlgorithmUpsertDTO(UUID.randomUUID(), "name", null, "1", null, null, null, null, "http://x", "/run",
                null, null, null, null, null, "author", null, null, null, null, null, List.of(), List.of(),
                List.of(), username, password, hasPassword);
    }

    @Test
    @DisplayName("The write-only password is read from a request body")
    void passwordIsReadFromARequest() throws Exception {
        final String body = "{\"authUsername\":\"svc\",\"authPassword\":\"" + PASSWORD + "\",\"authPasswordSet\":true}";

        final AlgorithmUpsertDTO dto = objectMapper.readValue(body, AlgorithmUpsertDTO.class);

        assertThat(dto.authUsername()).isEqualTo("svc");
        assertThat(dto.authPassword()).isEqualTo(PASSWORD);
    }

    @Test
    @DisplayName("The password is never written to JSON, only whether one is stored")
    void passwordIsNeverSerialized() throws Exception {
        final String json = objectMapper.writeValueAsString(upsert("svc", PASSWORD, true));
        final JsonNode node = objectMapper.readTree(json);

        assertThat(json).doesNotContain(PASSWORD);
        assertThat(node.has("authPassword")).isFalse();
        assertThat(node.get("authUsername").asText()).isEqualTo("svc");
        assertThat(node.get("authPasswordSet").asBoolean()).isTrue();
    }

    @Test
    void toStringHidesThePassword() {
        assertThat(upsert("svc", PASSWORD, null).toString()).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("The mapper shows authPasswordSet and never the ciphertext, in the list and the upsert shape")
    void mapperNeverExposesTheStoredPassword() throws Exception {
        final Algorithm algorithm = Algorithm.builder().id(UUID.randomUUID()).name("n")
                .authUsername("svc").authPassword(CIPHERTEXT).build();

        final AlgorithmDTO dto = mapper.toDTO(algorithm);
        final AlgorithmUpsertDTO upsertDto = mapper.toUpsertDTO(algorithm);

        assertThat(dto.authUsername()).isEqualTo("svc");
        assertThat(dto.authPasswordSet()).isTrue();
        assertThat(upsertDto.authPassword()).isNull();
        assertThat(upsertDto.authPasswordSet()).isTrue();
        assertThat(objectMapper.writeValueAsString(dto)).doesNotContain(CIPHERTEXT);
        assertThat(objectMapper.writeValueAsString(upsertDto)).doesNotContain(CIPHERTEXT);
    }

    @Test
    void mapperReportsNoPasswordWhenNoneIsStored() {
        final Algorithm algorithm = Algorithm.builder().id(UUID.randomUUID()).name("n").build();

        assertThat(mapper.toDTO(algorithm).authPasswordSet()).isFalse();
        assertThat(mapper.toUpsertDTO(algorithm).authPasswordSet()).isFalse();
    }

    @Test
    @DisplayName("The submitted credentials reach the create and update commands")
    void credentialsReachTheCommands() {
        final AlgorithmUpsertDTO dto = upsert("svc", PASSWORD, null);

        final CreateAlgorithmCommand create = mapper.toCreateCommand(dto, List.of(), List.of(), List.of());
        final UpdateAlgorithmCommand update = mapper.toUpdateCommand(dto, List.of(), List.of(), List.of());

        assertThat(create.authUsername()).isEqualTo("svc");
        assertThat(create.authPassword()).isEqualTo(PASSWORD);
        assertThat(update.authUsername()).isEqualTo("svc");
        assertThat(update.authPassword()).isEqualTo(PASSWORD);
    }
}
