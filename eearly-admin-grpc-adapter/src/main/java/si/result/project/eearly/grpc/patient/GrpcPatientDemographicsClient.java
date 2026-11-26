package si.result.project.eearly.grpc.patient;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import si.result.eearly.genproto.BackofficePatientServiceGrpc;
import si.result.eearly.genproto.Sex;
import si.result.eearly.genproto.UpsertPatientDemographicsRequest;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.port.patient.PatientDemographicsClient;

/**
 * Calls the mobile backend's admin-only {@code BackofficePatientService.UpsertPatientDemographics},
 * authenticated as this service like the schedule sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcPatientDemographicsClient implements PatientDemographicsClient {

    static final long DEADLINE_SECONDS = 10;

    private final GrpcChannelFactory channelFactory;
    private final ClientInterceptor grpcAuthInterceptor;

    @Override
    public void upsertDemographics(UUID keycloakId, LocalDate dateOfBirth, GenderType gender) {
        ManagedChannel managedChannel = channelFactory.createChannel("mobile-backend");
        try {
            Channel channel = ClientInterceptors.intercept(managedChannel, grpcAuthInterceptor);
            BackofficePatientServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .upsertPatientDemographics(toRequest(keycloakId, dateOfBirth, gender));
            log.debug("Synced demographics of patient {} to the mobile backend", keycloakId);
        } finally {
            shutdownChannel(managedChannel);
        }
    }

    static UpsertPatientDemographicsRequest toRequest(
            UUID keycloakId, LocalDate dateOfBirth, GenderType gender) {
        UpsertPatientDemographicsRequest.Builder request = UpsertPatientDemographicsRequest.newBuilder()
                .setUserId(keycloakId.toString())
                .setSex(toSex(gender));
        if (dateOfBirth != null) {
            request.setDateOfBirth(dateOfBirth.toString());
        }
        return request.build();
    }

    static Sex toSex(GenderType gender) {
        if (gender == null) {
            return Sex.UNKNOWN_SEX;
        }
        return switch (gender) {
            case MALE -> Sex.MALE;
            case FEMALE -> Sex.FEMALE;
            case OTHER -> Sex.OTHER;
        };
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
