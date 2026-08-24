package si.result.project.eearly.config.algorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlgorithmHttpClient {

    private final AlgorithmApiProperties properties;

    public HttpResponse<String> send(String method, String url, String body, long timeoutSeconds)
            throws IOException, InterruptedException {

        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header(HttpHeaders.ACCEPT, "application/json");

        applyAuthorization(builder);

        if ("POST".equals(method)) {
            builder.header(HttpHeaders.CONTENT_TYPE, "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build()) {
            final HttpResponse<String> response = client.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("Algorithm API {} {} -> {}", method, url, response.statusCode());
            return response;
        }
    }

    private void applyAuthorization(HttpRequest.Builder builder) {
        final AlgorithmApiProperties.BasicAuth basicAuth = properties.getBasicAuth();
        if (basicAuth != null && basicAuth.getUsername() != null && !basicAuth.getUsername().isBlank()) {
            final String creds = basicAuth.getUsername() + ":" +
                    (basicAuth.getPassword() != null ? basicAuth.getPassword() : "");
            builder.header(HttpHeaders.AUTHORIZATION, "Basic " +
                    Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
            return;
        }
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }
    }
}
