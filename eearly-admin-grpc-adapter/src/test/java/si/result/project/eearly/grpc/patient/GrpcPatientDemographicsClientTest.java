package si.result.project.eearly.grpc.patient;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.springframework.grpc.client.GrpcChannelFactory;
import si.result.eearly.genproto.BackofficePatientServiceGrpc;
import si.result.eearly.genproto.UpsertPatientDemographicsResponse;
import si.result.project.eearly.grpc.config.GrpcClientAuthConfig;
import si.result.project.eearly.port.mobile.MobileKeycloakService;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import si.result.eearly.genproto.Sex;
import si.result.eearly.genproto.UpsertPatientDemographicsRequest;
import si.result.project.eearly.model.gender.GenderType;

class GrpcPatientDemographicsClientTest {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private Server server;

    @AfterEach
    void stopServer() throws InterruptedException {
        if (server != null) {
            server.shutdownNow().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private GrpcPatientDemographicsClient clientAgainst(
            BackofficePatientServiceGrpc.BackofficePatientServiceImplBase service,
            AtomicReference<String> receivedAuthorization,
            String token) throws Exception {
        ServerInterceptor captureHeaders = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                receivedAuthorization.set(headers.get(AUTHORIZATION));
                return next.startCall(call, headers);
            }
        };
        server = NettyServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(service, captureHeaders))
                .build().start();

        GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
        when(channelFactory.createChannel(anyString()))
                .thenAnswer(invocation -> NettyChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build());
        MobileKeycloakService keycloak = mock(MobileKeycloakService.class);
        when(keycloak.getAccessTokenForClient()).thenReturn(token);
        return new GrpcPatientDemographicsClient(channelFactory, new GrpcClientAuthConfig(keycloak).grpcAuthInterceptor());
    }

    @Test
    void sendsTheServiceTokenAndTheDemographics() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<UpsertPatientDemographicsRequest> received = new AtomicReference<>();
        UUID keycloakId = UUID.randomUUID();
        GrpcPatientDemographicsClient client = clientAgainst(
                new BackofficePatientServiceGrpc.BackofficePatientServiceImplBase() {
                    @Override
                    public void upsertPatientDemographics(UpsertPatientDemographicsRequest request,
                            StreamObserver<UpsertPatientDemographicsResponse> responseObserver) {
                        received.set(request);
                        responseObserver.onNext(UpsertPatientDemographicsResponse.getDefaultInstance());
                        responseObserver.onCompleted();
                    }
                }, authorization, "test-token");

        client.upsertDemographics(keycloakId, LocalDate.of(2002, 3, 11), GenderType.FEMALE);

        assertThat(authorization.get()).isEqualTo("Bearer test-token");
        assertThat(received.get().getUserId()).isEqualTo(keycloakId.toString());
        assertThat(received.get().getDateOfBirth()).isEqualTo("2002-03-11");
        assertThat(received.get().getSex()).isEqualTo(Sex.FEMALE);
    }

    @Test
    void throwsWhenTheMobileBackendRejectsTheCall() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        GrpcPatientDemographicsClient client = clientAgainst(
                new BackofficePatientServiceGrpc.BackofficePatientServiceImplBase() {
                    @Override
                    public void upsertPatientDemographics(UpsertPatientDemographicsRequest request,
                            StreamObserver<UpsertPatientDemographicsResponse> responseObserver) {
                        responseObserver.onError(Status.UNAUTHENTICATED.asRuntimeException());
                    }
                }, authorization, "test-token");

        assertThatThrownBy(() -> client.upsertDemographics(UUID.randomUUID(), LocalDate.of(2002, 3, 11), GenderType.MALE))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void requestCarriesTheKeycloakIdAndAnIsoDateOfBirth() {
        UUID keycloakId = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");

        UpsertPatientDemographicsRequest request = GrpcPatientDemographicsClient.toRequest(
                keycloakId, LocalDate.of(2002, 3, 11), GenderType.MALE);

        assertThat(request.getUserId()).isEqualTo("50287036-1b84-4e2e-831f-91ab621e52d9");
        assertThat(request.getDateOfBirth()).isEqualTo("2002-03-11");
        assertThat(request.getSex()).isEqualTo(Sex.MALE);
    }

    @Test
    void missingValuesAreSentAsUnset() {
        UpsertPatientDemographicsRequest request = GrpcPatientDemographicsClient.toRequest(
                UUID.randomUUID(), null, null);

        assertThat(request.getDateOfBirth()).isEmpty();
        assertThat(request.getSex()).isEqualTo(Sex.UNKNOWN_SEX);
    }

    @ParameterizedTest
    @CsvSource({"MALE, MALE", "FEMALE, FEMALE", "OTHER, OTHER"})
    void mapsEveryGender(GenderType gender, Sex sex) {
        assertThat(GrpcPatientDemographicsClient.toSex(gender)).isEqualTo(sex);
    }
}
