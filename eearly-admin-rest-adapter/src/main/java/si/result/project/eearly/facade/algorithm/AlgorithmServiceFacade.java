package si.result.project.eearly.facade.algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatusCode;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.config.algorithm.AlgorithmHttpClient;
import si.result.project.eearly.dto.algorithm.AlgorithmDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmServiceStatusDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmStatusDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.algorithm.AlgorithmMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.util.JsonUtils;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Facade
@RequiredArgsConstructor
public class AlgorithmServiceFacade {
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 60;

    private final AlgorithmService algorithmService;
    private final AlgorithmHttpClient algorithmHttpClient;
    private final PatientService patientService;
    private final MeasurementService measurementService;
    private final AlgorithmMapper algorithmMapper;

    @Transactional(readOnly = true)
    public PagedModel<AlgorithmDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                algorithmService.findAll(filter.toSpecification(), pageable)
                        .map(algorithmMapper::toDTO)
        );
    }

    @Transactional(readOnly = true)
    public AlgorithmDTO getById(UUID id) {
        return algorithmMapper.toDTO(
                algorithmService.findById(id)
        );
    }

    @Transactional()
    public AlgorithmDTO create(final AlgorithmUpsertDTO algorithmUpsertDTO) {
        return algorithmMapper.toDTO(
                algorithmService.create(
                        algorithmMapper.toCreateCommand(
                                algorithmUpsertDTO,
                                getPatientRelations(algorithmUpsertDTO.patientList()),
                                getMeasurementRelations(algorithmUpsertDTO.measurementList()))));
    }

    @Transactional
    public AlgorithmUpsertDTO update(final AlgorithmUpsertDTO algorithmUpsertDTO) {
        if (algorithmUpsertDTO.id() == null) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_NOT_FOUND);
        }

        return algorithmMapper.toUpsertDTO(
                algorithmService.update(
                        algorithmMapper.toUpdateCommand(
                                algorithmUpsertDTO,
                                getPatientRelations(algorithmUpsertDTO.patientList()),
                                getMeasurementRelations(algorithmUpsertDTO.measurementList())
                        )
                )
        );
    }

    @Transactional
    public AlgorithmStatusDTO changeStatus(UUID id, AlgorithmStatusDTO statusDTO) {
        final var statusType = algorithmMapper.mapStatus(statusDTO.status());
        final var algorithm = algorithmService.changeStatus(id, statusType);

        return algorithmMapper.toStatusDTO(algorithm);
    }

    private List<Patient> getPatientRelations(List<UUID> patientIds) {
        if (patientIds != null && !patientIds.isEmpty()) {
            final var patients = patientService.findAllById(patientIds);
            if (patients.size() != patientIds.size()) {
                throw new DomainException(DomainExceptionCode.PATIENT_NOT_FOUND);
            }
            return patients;
        } else {
            return new ArrayList<>();
        }
    }

    private List<Measurement> getMeasurementRelations(List<UUID> measurementIds) {
        if (measurementIds != null && !measurementIds.isEmpty()) {
            final var measurements = measurementService.findAllById(measurementIds);
            if (measurements.size() != measurementIds.size()) {
                throw new DomainException(DomainExceptionCode.MEASUREMENT_NOT_FOUND);
            }
            return measurements;
        } else {
            return new ArrayList<>();
        }
    }

    public AlgorithmServiceStatusDTO getServiceStatus(UUID id) {
        final Algorithm algorithm = algorithmService.findById(id);

        if (algorithm.getHealthCheckEndpoint() == null || algorithm.getHealthCheckEndpoint().isBlank()) {
            return new AlgorithmServiceStatusDTO(false, null, DomainExceptionCode.ALGORITHM_SERVICE_CONFIGURATION_INVALID.getMessage());
        }

        try {
            final java.net.http.HttpResponse<String> response = algorithmHttpClient.send(
                    "GET", algorithm.getHealthUrl(), null, HEALTH_CHECK_TIMEOUT_SECONDS);

            log.info("Algorithm service health check {} -> {}", algorithm.getHealthUrl(), response.statusCode());

            if (HttpStatusCode.valueOf(response.statusCode()).is2xxSuccessful()) {
                return parseHealthyResponse(response.body());
            }

            return new AlgorithmServiceStatusDTO(false, null,
                    "HTTP " + response.statusCode() + ": " + response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AlgorithmServiceStatusDTO(false, null, e.getMessage());
        } catch (Exception e) {
            log.warn("{} {}", DomainExceptionCode.ALGORITHM_SERVICE_HEALTHCHECK_FAILED.getMessage(), id, e);
            return new AlgorithmServiceStatusDTO(false, null, e.getMessage());
        }
    }

    private AlgorithmServiceStatusDTO parseHealthyResponse(String body) {
        if (body == null || body.isBlank()) {
            return new AlgorithmServiceStatusDTO(true, null, null);
        }
        try {
            final JsonNode root = JsonUtils.mapper().readTree(body);
            final String version = extractFirstText(root, "version", "service_version", "api_version");
            final String message = extractFirstText(root, "status", "message", "detail");
            return new AlgorithmServiceStatusDTO(true, version, message);
        } catch (Exception e) {
            return new AlgorithmServiceStatusDTO(true, null,
                    body.length() > 200 ? body.substring(0, 200) : body);
        }
    }

    private String extractFirstText(JsonNode root, String... fields) {
        for (String field : fields) {
            if (root.hasNonNull(field)) {
                return root.path(field).asText(null);
            }
        }
        return null;
    }
}
