package si.result.project.eearly.facade.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.patient.PatientDTO;
import si.result.project.eearly.dto.patient.PatientLiteDTO;
import si.result.project.eearly.dto.patient.PatientMeasurementRowDTO;
import si.result.project.eearly.dto.patient.PatientMinimalDTO;
import si.result.project.eearly.dto.patient.PatientOnboardingDTO;
import si.result.project.eearly.dto.patient.PatientUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.patient.PatientMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementStatus;
import si.result.project.eearly.model.mobile.MobileUserResponse;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.PatientStatus;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementPage;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.project.eearly.port.mobile.MobileKeycloakService;
import si.result.project.eearly.port.mobile.MobileServiceService;
import si.result.project.eearly.port.patient.PatientMeasurementClient;
import si.result.project.eearly.port.patient.PatientService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;
import si.result.spring.boot.bricks.exception.DomainException;

import java.security.SecureRandom;
import java.util.*;

@Facade
@RequiredArgsConstructor
public class PatientServiceFacade {
    private final PatientService patientService;
    private final PatientMapper patientMapper;
    private final CaretakerService caretakerService;
    private final MeasurementService measurementService;
    private final AlgorithmService algorithmService;
    private final MobileKeycloakService mobileKeycloakService;
    private final MobileServiceService mobileServiceService;
    private final PatientMeasurementClient patientMeasurementClient;
    private final Random random = new SecureRandom();

    private static final Set<String> VIRTUAL_FIELDS = Set.of("patientStatus", "measurementStatus");

    @Transactional(readOnly = true)
    public PagedModel<PatientLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        boolean hasVirtualFieldSorting = pageable.getSort().stream()
                .anyMatch(sort -> VIRTUAL_FIELDS.contains(sort.getProperty()));

        if (hasVirtualFieldSorting) {
            return getPageWithVirtualFieldSorting(filter, pageable);
        } else {
            return new PagedModel<>(
                    patientService.findAll(filter.toSpecification(), pageable)
                            .map(this::mapPatientWithStatus));
        }
    }

    private PagedModel<PatientLiteDTO> getPageWithVirtualFieldSorting(final Filter filter, final Pageable pageable) {
        Pageable dbPageable = createDbPageable(pageable);

        Page<Patient> allPatients = patientService.findAll(filter.toSpecification(), dbPageable);

        List<PatientLiteDTO> mappedPatients = allPatients.getContent().stream()
                .map(this::mapPatientWithStatus).toList();

        List<PatientLiteDTO> sortedPatients = sortByVirtualFields(mappedPatients, pageable.getSort());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), sortedPatients.size());

        List<PatientLiteDTO> paginatedList = start >= sortedPatients.size() ?
                Collections.emptyList() : sortedPatients.subList(start, end);

        Page<PatientLiteDTO> resultPage = new PageImpl<>(paginatedList, pageable, allPatients.getTotalElements());

        return new PagedModel<>(resultPage);
    }

    private Pageable createDbPageable(final Pageable pageable) {
        List<Sort.Order> orders = pageable.getSort().stream()
                .filter(order -> !VIRTUAL_FIELDS.contains(order.getProperty()))
                .toList();

        Sort sort = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private List<PatientLiteDTO> sortByVirtualFields(List<PatientLiteDTO> patients, Sort sort) {
        Comparator<PatientLiteDTO> comparator = null;

        for (Sort.Order order : sort) {
            Comparator<PatientLiteDTO> orderComparator = createComparatorForField(order.getProperty());

            if (orderComparator != null) {
                if (order.getDirection() == Sort.Direction.DESC) {
                    orderComparator = orderComparator.reversed();
                }
                comparator = comparator == null ? orderComparator : comparator.thenComparing(orderComparator);
            }
        }

        if (comparator != null) {
            return patients.stream().sorted(comparator).toList();
        }

        return patients;
    }

    private Comparator<PatientLiteDTO> createComparatorForField(final String fieldName) {
        return switch (fieldName) {
            case "patientStatus" -> Comparator.comparing(PatientLiteDTO::patientStatus);
            case "measurementStatus" -> Comparator.comparing(PatientLiteDTO::measurementStatus);
            default -> null;
        };
    }

    private PatientLiteDTO mapPatientWithStatus(Patient patient) {
        return patientMapper.toLiteDTO(
                patient,
                getRandomPatientStatus(),
                getRandomMeasurementStatus()
        );
    }

    public PatientStatus getRandomPatientStatus() {
        PatientStatus[] values = PatientStatus.values();
        return values[random.nextInt(values.length)];
    }

    public MeasurementStatus getRandomMeasurementStatus() {
        MeasurementStatus[] values = MeasurementStatus.values();
        return values[random.nextInt(values.length)];
    }

    @Transactional(readOnly = true)
    public PatientDTO getById(final UUID uuid) {
        return patientMapper.toDTO(patientService.findById(uuid));
    }

    @Transactional(readOnly = true)
    public PagedModel<PatientMeasurementRowDTO> getMeasurementsById(final UUID uuid, Pageable pageable) {
        return getMeasurementsById(uuid, pageable, null, null);
    }

    @Transactional(readOnly = true)
    public PagedModel<PatientMeasurementRowDTO> getMeasurementsById(
            final UUID uuid, Pageable pageable, String startDate, String endDate) {
        Patient patient = patientService.findById(uuid);
        if (patient.getKeycloakId() == null) {
            throw new DomainException(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED,
                    "Patient does not have Keycloak ID");
        }

        PatientMeasurementPage measurements = patientMeasurementClient.getMeasurements(
                patient.getKeycloakId(), patient.getMeasurements(), pageable, startDate, endDate);

        return new PagedModel<>(new PageImpl<>(
                patientMapper.toMeasurementRowDTOs(measurements.measurements()),
                PageRequest.of(measurements.page(), measurements.size()),
                measurements.totalElements()));
    }

    @Transactional()
    public PatientOnboardingDTO create(final PatientUpsertDTO patientUpsertDTO) {
        isHealthInsuranceIdSaved(patientUpsertDTO.healthInsuranceId());
        var patient = patientService.create(
                patientMapper.toCreateCommand(
                        patientUpsertDTO,
                        getCaretakerRelations(patientUpsertDTO.caretakerList()),
                        getMeasurementRelations(patientUpsertDTO.measurementList()),
                        getAlgorithmRelations(patientUpsertDTO.algorithmsList())));

        MobileUserResponse createMobileUserResponse = mobileServiceService.createUser(
                patientMapper.toCreateCommand(patient),
                mobileKeycloakService.getAccessTokenForClient());

        String keycloakUserId = createMobileUserResponse.user().id();
        patient = patientService.updateKeycloakId(patient.getId(), UUID.fromString(keycloakUserId));

        return patientMapper.toOnboardingDTO(patient, createMobileUserResponse.onboardingUrl());
    }

    @Transactional()
    public PatientMinimalDTO update(final UUID uuid, final PatientUpsertDTO patientUpsertDTO) {
        return patientMapper.toMinimalDTO(
                patientService.update(
                        patientMapper.toUpdateCommand(
                                uuid,
                                patientUpsertDTO,
                                getCaretakerRelations(patientUpsertDTO.caretakerList()),
                                getMeasurementRelations(patientUpsertDTO.measurementList()),
                                getAlgorithmRelations(patientUpsertDTO.algorithmsList()))));
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

    private List<Caretaker> getCaretakerRelations(List<UUID> caretakerIds) {
        if (caretakerIds != null && !caretakerIds.isEmpty()) {
            final var caretakers = caretakerService.findAllById(caretakerIds);
            if (caretakers.size() != caretakerIds.size()) {
                throw new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND);
            }
           return caretakers;
        } else {
            return new ArrayList<>();
        }
    }

    private List<Algorithm> getAlgorithmRelations(List<UUID> algorithmIds) {
        if (algorithmIds != null && !algorithmIds.isEmpty()) {
            final var algorithms = algorithmService.findAllById(algorithmIds);
            if (algorithms.size() != algorithmIds.size()) {
                throw new DomainException(DomainExceptionCode.ALGORITHM_NOT_FOUND);
            }
            return algorithms;
        } else {
            return new ArrayList<>();
        }
    }

    private void isHealthInsuranceIdSaved(String healthInsuranceId) {
        if (healthInsuranceId == null) {
            throw new DomainException(DomainExceptionCode.PATIENT_HEALTH_INSURANCE_ID_NULL);
        }
        final var patient = patientService.findByHealthInsuranceId(healthInsuranceId);
        if (patient != null) {
            throw new DomainException(DomainExceptionCode.PATIENT_HEALTH_INSURANCE_ID_FOUND);
        }
    }
}
