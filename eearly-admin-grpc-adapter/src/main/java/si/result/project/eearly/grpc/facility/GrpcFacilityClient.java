package si.result.project.eearly.grpc.facility;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import si.result.eearly.genproto.ErrorCode;
import si.result.eearly.genproto.Facility;
import si.result.eearly.genproto.FacilityServiceGrpc;
import si.result.eearly.genproto.UpsertFacilityRequest;
import si.result.eearly.genproto.UpsertFacilityResponse;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.organization.OrganizationClient;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcFacilityClient implements OrganizationClient {

    private final GrpcChannelFactory channelFactory;
    private final ClientInterceptor grpcAuthInterceptor;

    @Override
    public void upsertFacility(Organization organization) {
        log.info("Upserting facility for organization: {}", organization.getId());

        ManagedChannel channel = channelFactory.createChannel("mobile-backend");
        // The mobile service accepts UpsertFacility only from the admin service client.
        Channel authenticatedChannel = ClientInterceptors.intercept(channel, grpcAuthInterceptor);
        FacilityServiceGrpc.FacilityServiceFutureStub stub = FacilityServiceGrpc.newFutureStub(authenticatedChannel);

        Facility facility = Facility.newBuilder()
                .setId(organization.getId().toString())
                .setName(organization.getName())
                .setDescription(organization.getName())
                .build();

        UpsertFacilityRequest request = UpsertFacilityRequest.newBuilder()
                .setFacility(facility)
                .build();

        ListenableFuture<UpsertFacilityResponse> futureResponse;
        try {
            futureResponse = stub.upsertFacility(request);
        } catch (RuntimeException e) {
            // For example no access token: log like any other failed call, never break the caller.
            log.error("Error calling FacilityService.UpsertFacility for organization {}: {}",
                    organization.getId(), e.getMessage(), e);
            shutdownChannel(channel);
            return;
        }

        Futures.addCallback(futureResponse, new FutureCallback<>() {
            @Override
            public void onSuccess(UpsertFacilityResponse response) {
                try {
                    if (response.hasError() && response.getError().getCode() != ErrorCode.OK) {
                        handleErrorResponse(organization.getId(), response.getError());
                    } else {
                        log.info("Successfully upserted facility for organization: {}", organization.getId());
                    }
                } finally {
                    shutdownChannel(channel);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Error calling FacilityService.UpsertFacility for organization {}: {}",
                        organization.getId(), t.getMessage(), t);
                shutdownChannel(channel);
            }
        }, MoreExecutors.directExecutor());
    }

    private void handleErrorResponse(java.util.UUID organizationId, si.result.eearly.genproto.Error error) {
        ErrorCode errorCode = error.getCode();
        String errorMessage = error.getMessage();

        switch (errorCode) {
            case VALIDATION_ERROR -> log.warn("Validation error upserting facility for organization {}: {}",
                    organizationId, errorMessage);
            case ALREADY_EXISTS -> log.info("Facility already exists for organization {}: {}",
                    organizationId, errorMessage);
            case INTERNAL_ERROR -> log.error("Internal error upserting facility for organization {}: {}",
                    organizationId, errorMessage);
            default -> log.error("Unknown error upserting facility for organization {}: {} - {}",
                    organizationId, errorCode, errorMessage);
        }
    }

    private void shutdownChannel(ManagedChannel channel) {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Channel shutdown interrupted", e);
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
