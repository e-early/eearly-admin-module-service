package si.result.project.eearly.port.mobile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.mobile.CreateMobileUserCommand;
import si.result.project.eearly.model.mobile.MobileUserResponse;
import si.result.spring.boot.bricks.exception.DomainException;
import org.springframework.http.HttpStatus;

@Service
@Slf4j
public class MobileServiceService {

  private final RestTemplate mobileServiceRestTemplate;

  public MobileServiceService(
      @Qualifier("mobileServiceRestTemplate") RestTemplate mobileServiceRestTemplate) {
    this.mobileServiceRestTemplate = mobileServiceRestTemplate;
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

      log.error("Failed to create user in mobile service. status={}, body={}", status, body);
      throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED);
    }

  }
}
