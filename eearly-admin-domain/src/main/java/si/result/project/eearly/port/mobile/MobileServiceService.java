package si.result.project.eearly.port.mobile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.mobile.CreateMobileUserCommand;
import si.result.project.eearly.model.mobile.MobileOnboardingLinkResponse;
import si.result.project.eearly.model.mobile.MobileUserResponse;
import si.result.spring.boot.bricks.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Calls of the mobile service. Onboarding links and the bodies of its answers are credentials or personal data:
 * they are never logged, only the status of a failed call is.
 */
@Service
@Slf4j
public class MobileServiceService {

  private final RestTemplate mobileServiceRestTemplate;
  private final MobileKeycloakService mobileKeycloakService;

  public MobileServiceService(
      @Qualifier("mobileServiceRestTemplate") RestTemplate mobileServiceRestTemplate,
      MobileKeycloakService mobileKeycloakService) {
    this.mobileServiceRestTemplate = mobileServiceRestTemplate;
    this.mobileKeycloakService = mobileKeycloakService;
  }

  public MobileUserResponse createUser(CreateMobileUserCommand command, String authToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(authToken);

    HttpEntity<CreateMobileUserCommand> request = new HttpEntity<>(command, headers);

    try {
      var response = mobileServiceRestTemplate.exchange(
          "/onboarding/create-user",
          HttpMethod.POST,
          request,
          MobileUserResponse.class
      );
      return response.getBody();
    } catch (HttpStatusCodeException e) {
      var status = e.getStatusCode();
      var body = e.getResponseBodyAsString();

      if (status == HttpStatus.CONFLICT || status == HttpStatus.BAD_REQUEST) {
        if (body.contains("email")) {
          throw new DomainException(DomainExceptionCode.PATIENT_EMAIL_FOUND);
        }
      }

      log.error("Failed to create user in mobile service. status={}", status);
      throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED);
    }

  }

  /**
   * Asks the mobile service for a new single-use onboarding link of the user. Every call invalidates the user's
   * earlier unused links. Called with the service's own client token.
   *
   * @return the link, never null or blank
   * @throws DomainException {@code MOBILE_USER_NOT_FOUND} when the mobile service does not know the user,
   *                         {@code MOBILE_SERVICE_REQUEST_FAILED} for any other failure
   */
  public String issueOnboardingLink(UUID keycloakUserId) {
    final String authToken = clientToken();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(authToken);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    try {
      var response = mobileServiceRestTemplate.exchange(
          "/onboarding/users/{keycloakUserId}/onboarding-link",
          HttpMethod.POST,
          request,
          MobileOnboardingLinkResponse.class,
          keycloakUserId
      );
      final var body = response.getBody();
      if (body == null || body.onboardingUrl() == null || body.onboardingUrl().isBlank()) {
        log.error("Mobile service returned no onboarding link. status={}", response.getStatusCode());
        throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED);
      }
      return body.onboardingUrl();
    } catch (HttpStatusCodeException e) {
      var status = e.getStatusCode();
      if (status == HttpStatus.NOT_FOUND) {
        log.error("User not found in mobile service when issuing an onboarding link. status={}", status);
        throw new DomainException(DomainExceptionCode.MOBILE_USER_NOT_FOUND);
      }
      log.error("Failed to issue an onboarding link in mobile service. status={}", status);
      throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED);
    } catch (RestClientException e) {
      // Connection failure, unreadable answer: the message may quote the answer, so only the type is logged.
      log.error("Failed to issue an onboarding link in mobile service. error={}", e.getClass().getSimpleName());
      throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED);
    }
  }

  private String clientToken() {
    final String token;
    try {
      token = mobileKeycloakService.getAccessTokenForClient();
    } catch (RuntimeException e) {
      log.error("Failed to get a client token for the mobile service. error={}", e.getClass().getSimpleName());
      throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED);
    }
    if (token == null || token.isBlank()) {
      log.error("Failed to get a client token for the mobile service. error=empty token");
      throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED);
    }
    return token;
  }
}
