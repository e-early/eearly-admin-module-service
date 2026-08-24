package si.result.project.eearly.grpc.config;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import si.result.project.eearly.port.mobile.MobileKeycloakService;

/**
 * Configuration for gRPC client authentication.
 * Adds Bearer token to all gRPC calls to mobile-service.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcClientAuthConfig {

    private final MobileKeycloakService mobileKeycloakService;

    @Bean
    public ClientInterceptor grpcAuthInterceptor() {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next) {

                return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                        next.newCall(method, callOptions)) {

                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        try {
                            String token = mobileKeycloakService.getAccessTokenForClient();
                            headers.put(
                                    Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                                    "Bearer " + token
                            );
                            log.debug("Added Bearer token to gRPC call for method: {}", method.getFullMethodName());
                        } catch (Exception e) {
                            log.error("Failed to get access token for gRPC call to method: {}",
                                    method.getFullMethodName(), e);
                            throw new RuntimeException("Failed to get access token for gRPC call", e);
                        }
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }
}