package si.result.project.eearly.facade.caretaker;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerLiteDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.department.DepartmentService;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;

import java.util.UUID;

@Facade
@RequiredArgsConstructor
public class CaretakerServiceFacade {
    private final CaretakerService caretakerService;
    private final CaretakerMapper caretakerMapper;
    private final OrganizationService organizationService;
    private final DepartmentService departmentService;

    @Transactional(readOnly = true)
    public PagedModel<CaretakerLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                caretakerService.findAll(filter.toSpecification(), pageable)
                        .map(caretakerMapper::toLiteDTO)
        );
    }

    @Transactional(readOnly = true)
    public CaretakerDTO getById(final UUID id) {
        return caretakerMapper.toDTO(caretakerService.findById(id));
    }

    @Transactional(readOnly = true)
    public CaretakerDTO getByKeycloakId(final UUID keycloakId) {
        return caretakerMapper.toDTO(caretakerService.findByKeycloakId(keycloakId));
    }

    @Transactional()
    public CaretakerDTO create(final CaretakerUpsertDTO caretakerUpsertDTO) {
        return caretakerMapper.toDTO(
                caretakerService.create(caretakerMapper.toCreateCommand(
                        caretakerUpsertDTO,
                        getOrganizationById(caretakerUpsertDTO.organizationId()),
                        getDepartmentById(caretakerUpsertDTO.departmentId()))));
    }

    @Transactional()
    public CaretakerDTO update(final UUID uuid, final CaretakerUpsertDTO caretakerUpsertDTO) {
        return caretakerMapper.toDTO(
                caretakerService.update(caretakerMapper.toUpdateCommand(
                        uuid,
                        caretakerUpsertDTO,
                        getOrganizationById(caretakerUpsertDTO.organizationId()),
                        getDepartmentById(caretakerUpsertDTO.departmentId()))));
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
