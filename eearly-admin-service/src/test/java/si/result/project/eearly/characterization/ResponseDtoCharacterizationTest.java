package si.result.project.eearly.characterization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import si.result.spring.boot.bricks.dto.ResponseDTO;

/**
 * Characterization of the response wrapper {@link ResponseDTO} from {@code si.result.lib:spring-boot-bricks}:
 * HTTP status and JSON of ok() / created() / deleted().
 */
class ResponseDtoCharacterizationTest {

    record Item(String name, int count) {
    }

    @RestController
    static class WrappingController {

        private static Object payload(String kind) {
            return switch (kind) {
                case "null" -> null;
                case "string" -> "hello";
                case "object" -> new Item("a", 1);
                case "list" -> List.of(new Item("a", 1), new Item("b", 2));
                case "empty-list" -> List.of();
                case "paged" -> new PagedModel<>(new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5));
                default -> throw new IllegalArgumentException(kind);
            };
        }

        @GetMapping("/ok")
        ResponseEntity<ResponseDTO<Object>> ok(@RequestParam String kind) {
            return new ResponseDTO<>(payload(kind)).ok();
        }

        @GetMapping("/created")
        ResponseEntity<ResponseDTO<Object>> created(@RequestParam String kind) {
            return new ResponseDTO<>(payload(kind)).created();
        }

        @GetMapping("/deleted")
        ResponseEntity<ResponseDTO<Object>> deleted(@RequestParam String kind) {
            return new ResponseDTO<>(payload(kind)).deleted();
        }

        @GetMapping("/no-arg")
        ResponseEntity<ResponseDTO<Object>> noArg() {
            return new ResponseDTO<>().ok();
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WrappingController()).build();

    private void assertBody(String path, String kind, int status, String json) throws Exception {
        mvc.perform(get(path).param("kind", kind))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(json, JsonCompareMode.STRICT));
    }

    // ---- ok() -------------------------------------------------------------------------------

    @Test
    void okWrapsStringPayloadWith200() throws Exception {
        assertBody("/ok", "string", 200, "{\"payload\":\"hello\"}");
    }

    @Test
    void okWrapsObjectPayloadWith200() throws Exception {
        assertBody("/ok", "object", 200, "{\"payload\":{\"name\":\"a\",\"count\":1}}");
    }

    @Test
    void okWrapsListPayloadWith200() throws Exception {
        assertBody("/ok", "list", 200,
                "{\"payload\":[{\"name\":\"a\",\"count\":1},{\"name\":\"b\",\"count\":2}]}");
        assertBody("/ok", "empty-list", 200, "{\"payload\":[]}");
    }

    @Test
    void okWithNullPayloadStillSerializesPayloadNullWith200() throws Exception {
        assertBody("/ok", "null", 200, "{\"payload\":null}");
    }

    @Test
    void noArgConstructorIsEquivalentToNullPayload() throws Exception {
        mvc.perform(get("/no-arg"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"payload\":null}", JsonCompareMode.STRICT));
    }

    @Test
    void okWrapsPagedModelPayloadWithContentAndPageBlock() throws Exception {
        assertBody("/ok", "paged", 200,
                "{\"payload\":{\"content\":[\"a\",\"b\"],"
                        + "\"page\":{\"size\":2,\"number\":0,\"totalElements\":5,\"totalPages\":3}}}");
    }

    // ---- created() --------------------------------------------------------------------------

    @Test
    void createdReturns201WithPayload() throws Exception {
        assertBody("/created", "object", 201, "{\"payload\":{\"name\":\"a\",\"count\":1}}");
    }

    @Test
    void createdWithNullPayloadStillReturns201WithPayloadNull() throws Exception {
        assertBody("/created", "null", 201, "{\"payload\":null}");
    }

    // ---- deleted() --------------------------------------------------------------------------

    @Test
    void deletedWithPayloadReturns200WithPayload() throws Exception {
        assertBody("/deleted", "object", 200, "{\"payload\":{\"name\":\"a\",\"count\":1}}");
    }

    @Test
    void deletedWithNullPayloadReturns204WithEmptyBodyAndNoContentType() throws Exception {
        MvcResult result = mvc.perform(get("/deleted").param("kind", "null"))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getContentType()).isNull();
    }

    // ---- Plain-object level (no HTTP) -------------------------------------------------------

    @Test
    void responseEntitiesCarryTheDtoItselfAsBodyAndExpectedStatus() {
        ResponseDTO<String> dto = new ResponseDTO<>("x");

        assertThat(dto.ok().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dto.ok().getBody()).isSameAs(dto);
        assertThat(dto.created().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(dto.created().getBody()).isSameAs(dto);
        assertThat(dto.deleted().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dto.deleted().getBody()).isSameAs(dto);

        ResponseDTO<String> empty = new ResponseDTO<>();
        assertThat(empty.payload()).isNull();
        assertThat(empty.deleted().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(empty.deleted().getBody()).isNull();
    }
}
