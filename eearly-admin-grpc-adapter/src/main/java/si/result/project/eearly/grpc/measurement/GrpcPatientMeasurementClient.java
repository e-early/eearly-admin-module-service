package si.result.project.eearly.grpc.measurement;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import si.result.eearly.genproto.GetMeasurementTypesRequestEmpty;
import si.result.eearly.genproto.GetMeasurementsForUserRequest;
import si.result.eearly.genproto.MeasurementServiceGrpc;
import si.result.eearly.genproto.MeasurementTypeServiceGrpc;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementPage;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementRow;
import si.result.project.eearly.port.patient.PatientMeasurementClient;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcPatientMeasurementClient implements PatientMeasurementClient {

    private final GrpcChannelFactory channelFactory;
    private final ClientInterceptor grpcAuthInterceptor;
    private final PatientMeasurementMapper patientMeasurementMapper;

    @Override
    public List<PatientMeasurementRow> getMeasurements(UUID patientKeycloakId, List<Measurement> measurements) {
        return getMeasurements(patientKeycloakId, measurements, null).measurements();
    }

    @Override
    public PatientMeasurementPage getMeasurements(UUID patientKeycloakId, List<Measurement> measurements,
                                                  Pageable pageable) {
        return getMeasurements(patientKeycloakId, measurements, pageable, null, null);
    }

    @Override
    public PatientMeasurementPage getMeasurements(UUID patientKeycloakId, List<Measurement> measurements,
                                                  Pageable pageable, String startDate, String endDate) {
        if (measurements == null || measurements.isEmpty()) {
            return emptyPage(pageable);
        }

        List<String> mobileMeasurementTypes = patientMeasurementMapper.toMobileMeasurementTypes(measurements);

        if (mobileMeasurementTypes.isEmpty()) {
            return emptyPage(pageable);
        }

        ManagedChannel managedChannel = channelFactory.createChannel("mobile-backend");
        Channel channelWithAuth = ClientInterceptors.intercept(managedChannel, grpcAuthInterceptor);

        try {
            Map<String, si.result.eearly.genproto.MeasurementType> mobileMeasurementTypeById =
                    getMobileMeasurementTypes(channelWithAuth);

            GetMeasurementsForUserRequest.Builder requestBuilder = GetMeasurementsForUserRequest.newBuilder()
                    .setUserId(patientKeycloakId.toString())
                    .addAllMeasurementTypes(mobileMeasurementTypes)
                    .setPage(pageable == null ? 0 : pageable.getPageNumber())
                    .setSize(pageable == null ? 0 : pageable.getPageSize());

            if (startDate != null) {
                requestBuilder.setStartDateTime(startDate);
            }
            if (endDate != null) {
                requestBuilder.setEndDateTime(endDate);
            }

            GetMeasurementsForUserRequest request = requestBuilder.build();

            var response = MeasurementServiceGrpc.newBlockingStub(channelWithAuth)
                    .getMeasurementsForUser(request);

            return new PatientMeasurementPage(
                    patientMeasurementMapper.toPatientMeasurementRows(
                            response.getMeasurementsList(), mobileMeasurementTypeById),
                    response.getPage(),
                    response.getSize(),
                    response.getTotalElements(),
                    response.getTotalPages());
        } catch (StatusRuntimeException e) {
            log.error("Error calling MeasurementService.GetMeasurementsForUser for patient {}: {}",
                    patientKeycloakId, e.getMessage(), e);
            throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED, e.getMessage());
        } finally {
            shutdownChannel(managedChannel);
        }
    }

    private PatientMeasurementPage emptyPage(Pageable pageable) {
        return new PatientMeasurementPage(
                List.of(),
                pageable == null ? 0 : pageable.getPageNumber(),
                pageable == null ? 0 : pageable.getPageSize(),
                0,
                0);
    }

    private Map<String, si.result.eearly.genproto.MeasurementType> getMobileMeasurementTypes(Channel channel) {
        var response = MeasurementTypeServiceGrpc.newBlockingStub(channel)
                .getMeasurementTypes(GetMeasurementTypesRequestEmpty.newBuilder().build());

        Map<String, si.result.eearly.genproto.MeasurementType> measurementTypes = new LinkedHashMap<>();
        for (si.result.eearly.genproto.MeasurementType measurementType : response.getMeasurementTypesList()) {
            measurementTypes.put(measurementType.getMeasurementTypeId(), measurementType);
        }
        return measurementTypes;
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
