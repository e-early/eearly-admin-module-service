package si.result.project.eearly.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.client.GrpcChannelFactory;
import si.result.eearly.genproto.CaretakerServiceGrpc;
import si.result.eearly.genproto.FacilityServiceGrpc;
import si.result.eearly.genproto.UpsertCaretakerRequest;
import si.result.eearly.genproto.UpsertCaretakerResponse;
import si.result.eearly.genproto.UpsertFacilityRequest;
import si.result.eearly.genproto.UpsertFacilityResponse;
import si.result.project.eearly.grpc.caretaker.GrpcCaretakerClient;
import si.result.project.eearly.grpc.config.GrpcClientAuthConfig;
import si.result.project.eearly.grpc.facility.GrpcFacilityClient;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.mobile.MobileKeycloakService;

/**
 * The mobile service accepts UpsertFacility and UpsertCaretaker only from the admin service client,
 * so both calls must carry the admin service's access token. A local gRPC server records the Authorization header that
 * actually arrives.
 */
class MobileRecordSyncClientsAuthTest {

    private static final String TEST_TOKEN = "test-admin-service-token";

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final BlockingQueue<String> receivedAuthorization = new LinkedBlockingQueue<>();
    private final MobileKeycloakService keycloak = mock(MobileKeycloakService.class);
    private final GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
    private Server server;

    private GrpcFacilityClient facilityClient;
    private GrpcCaretakerClient caretakerClient;
    private Organization organization;
    private Caretaker caretaker;

    @BeforeEach
    void setUp() throws Exception {
        ServerInterceptor recordAuthorization = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                    ServerCallHandler<Q, R> next) {
                String value = headers.get(AUTHORIZATION);
                receivedAuthorization.add(value == null ? "<none>" : value);
                return next.startCall(call, headers);
            }
        };
        server = NettyServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(new FacilityServiceGrpc.FacilityServiceImplBase() {
                    @Override
                    public void upsertFacility(UpsertFacilityRequest request,
                            StreamObserver<UpsertFacilityResponse> observer) {
                        observer.onNext(UpsertFacilityResponse.getDefaultInstance());
                        observer.onCompleted();
                    }
                }, recordAuthorization))
                .addService(ServerInterceptors.intercept(new CaretakerServiceGrpc.CaretakerServiceImplBase() {
                    @Override
                    public void upsertCaretaker(UpsertCaretakerRequest request,
                            StreamObserver<UpsertCaretakerResponse> observer) {
                        observer.onNext(UpsertCaretakerResponse.getDefaultInstance());
                        observer.onCompleted();
                    }
                }, recordAuthorization))
                .build()
                .start();
        int port = server.getPort();
        when(channelFactory.createChannel("mobile-backend")).thenAnswer(invocation ->
                ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build());

        var authInterceptor = new GrpcClientAuthConfig(keycloak).grpcAuthInterceptor();
        facilityClient = new GrpcFacilityClient(channelFactory, authInterceptor);
        caretakerClient = new GrpcCaretakerClient(channelFactory, authInterceptor);

        organization = mock(Organization.class);
        when(organization.getId()).thenReturn(UUID.randomUUID());
        when(organization.getName()).thenReturn("Test Organization");
        caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(UUID.randomUUID());
        when(caretaker.getFirstName()).thenReturn("Test");
        when(caretaker.getLastName()).thenReturn("Caretaker");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void upsertFacilitySendsTheAdminServiceToken() throws Exception {
        when(keycloak.getAccessTokenForClient()).thenReturn(TEST_TOKEN);

        facilityClient.upsertFacility(organization);

        assertThat(receivedAuthorization.poll(5, TimeUnit.SECONDS))
                .isEqualTo("Bearer " + TEST_TOKEN);
    }

    @Test
    void upsertCaretakerSendsTheAdminServiceToken() throws Exception {
        when(keycloak.getAccessTokenForClient()).thenReturn(TEST_TOKEN);

        caretakerClient.upsertCaretaker(caretaker);

        assertThat(receivedAuthorization.poll(5, TimeUnit.SECONDS))
                .isEqualTo("Bearer " + TEST_TOKEN);
    }

    @Test
    void missingTokenIsLoggedAndDoesNotBreakTheCaller() throws Exception {
        when(keycloak.getAccessTokenForClient()).thenThrow(new IllegalStateException("Token service unavailable"));

        assertThatCode(() -> facilityClient.upsertFacility(organization)).doesNotThrowAnyException();
        assertThatCode(() -> caretakerClient.upsertCaretaker(caretaker)).doesNotThrowAnyException();
        // Nothing is sent without a token.
        assertThat(receivedAuthorization.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }
}
