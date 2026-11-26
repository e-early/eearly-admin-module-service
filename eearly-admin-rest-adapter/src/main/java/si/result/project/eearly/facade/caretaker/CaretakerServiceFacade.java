package si.result.project.eearly.facade.caretaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.auth.AdminRoles;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerLiteDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.department.DepartmentService;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

@Facade
@RequiredArgsConstructor
@Slf4j
public class CaretakerServiceFacade {
    private final CaretakerService caretakerService;
    private final CaretakerMapper caretakerMapper;
    private final OrganizationService organizationService;
    private final DepartmentService departmentService;
    private final AuthenticationProvider authenticationProvider;

    @Transactional(readOnly = true)
    public PagedModel<CaretakerLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                caretakerService.findAll(filter.toSpecification(), pageable)
                        .map(caretakerMapper::toLiteDTO)
        );
    }

    /**
     * The caretaker, for the caretaker themself or an administrator.
     */
    @Transactional(readOnly = true)
    public CaretakerDTO getById(final UUID id) {
        return caretakerMapper.toDTO(findAccessibleCaretaker(id));
    }

    /**
     * The caretaker, for the caretaker themself or an administrator.
     */
    @Transactional(readOnly = true)
    public CaretakerDTO getByKeycloakId(final UUID keycloakId) {
        final Caretaker caretaker = caretakerService.findByKeycloakId(keycloakId);
        assertSelfOrAdministrator(caretaker);
        return caretakerMapper.toDTO(caretaker);
    }

    @Transactional()
    public CaretakerDTO create(final CaretakerUpsertDTO caretakerUpsertDTO) {
        return caretakerMapper.toDTO(
                caretakerService.create(caretakerMapper.toCreateCommand(
                        caretakerUpsertDTO,
                        getOrganizationById(caretakerUpsertDTO.organizationId()),
                        getDepartmentById(caretakerUpsertDTO.departmentId()))));
    }

    /**
     * Updates the caretaker, for the caretaker themself (their profile) or an administrator.
     */
    @Transactional()
    public CaretakerDTO update(final UUID uuid, final CaretakerUpsertDTO caretakerUpsertDTO) {
        findAccessibleCaretaker(uuid);
        return caretakerMapper.toDTO(
                caretakerService.update(caretakerMapper.toUpdateCommand(
                        uuid,
                        caretakerUpsertDTO,
                        getOrganizationById(caretakerUpsertDTO.organizationId()),
                        getDepartmentById(caretakerUpsertDTO.departmentId()))));
    }

    private Caretaker findAccessibleCaretaker(final UUID id) {
        final Caretaker caretaker = caretakerService.findById(id);
        assertSelfOrAdministrator(caretaker);
        return caretaker;
    }

    // Another caretaker gets CARETAKER_NOT_FOUND (404): whether the caretaker exists isn't theirs
    // to know. "Self" is the caretaker whose Keycloak id is the token's subject.
    private void assertSelfOrAdministrator(final Caretaker caretaker) {
        final String subject = authenticationProvider.getId();
        final boolean self = caretaker.getKeycloakId() != null
                && caretaker.getKeycloakId().toString().equals(subject);
        if (!self && !AdminRoles.isAdministrator(authenticationProvider)) {
            log.error("Caretaker {} is not accessible to user {}", caretaker.getId(), subject);
            throw new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND);
        }
    }

    // A missing id is passed on as null so that the caretaker rejects it (CARETAKER_ORGANIZATION_NULL);
    // an unknown id is ORGANIZATION_NOT_FOUND from the organization service.
    private Organization getOrganizationById(final UUID id) {
        return id == null ? null : organizationService.findById(id);
    }

    private Department getDepartmentById(final UUID id) {
        return id == null ? null : departmentService.findById(id);
    }
}
