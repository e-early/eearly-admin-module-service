package si.result.project.eearly.port.mobile;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class MobileKeycloakService
{
  @Value("${mobile.keycloak.realm}")
  private String realm;

  @Value("${mobile.keycloak.admin-client.id}")
  private String clientId;

  @Value("${mobile.keycloak.admin-client.secret}")
  private String clientSecret;


  private final RestTemplate mobileKeycloakRestTemplate;

  public MobileKeycloakService(
      @Qualifier("mobileKeycloakRestTemplate") RestTemplate mobileKeycloakRestTemplate) {
    this.mobileKeycloakRestTemplate = mobileKeycloakRestTemplate;
  }

  public String getAccessTokenForClient() {
    String tokenUrl = "/realms/" + realm + "/protocol/openid-connect/token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "client_credentials");
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

    ResponseEntity<Map<String, Object>> response = mobileKeycloakRestTemplate.exchange(
        tokenUrl,
        HttpMethod.POST,
        request,
        new ParameterizedTypeReference<>() {
        }
    );

    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
      return (String) response.getBody().get("access_token");
    } else {
      throw new RuntimeException("Failed to fetch token from Keycloak: " + response.getStatusCode());
    }  }

}
