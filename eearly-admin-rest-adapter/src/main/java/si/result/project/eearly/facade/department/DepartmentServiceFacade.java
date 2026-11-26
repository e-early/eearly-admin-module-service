package si.result.project.eearly.facade.department;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.department.DepartmentDTO;
import si.result.project.eearly.dto.department.DepartmentLiteDTO;
import si.result.project.eearly.dto.department.DepartmentUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.department.DepartmentMapper;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.department.DepartmentService;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

@Facade
@RequiredArgsConstructor
public class DepartmentServiceFacade {
    private final DepartmentService departmentService;
    private final OrganizationService organizationService;
    private final DepartmentMapper departmentMapper;

    @Transactional(readOnly = true)
    public PagedModel<DepartmentLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                departmentService.findAll(filter.toSpecification(), pageable)
                        .map(departmentMapper::toLiteDTO)
        );
    }

    @Transactional(readOnly = true)
    public DepartmentDTO getById(final UUID id) {
        return departmentMapper.toDTO(departmentService.findById(id));
    }

    @Transactional()
    public DepartmentLiteDTO create(final DepartmentUpsertDTO departmentUpsertDTO) {
        return departmentMapper.toLiteDTO(
                departmentService.create(
                        departmentMapper.toCreateCommand(
                                departmentUpsertDTO,
                                getOrganization(departmentUpsertDTO.organizationId()))));
    }

    @Transactional()
    public DepartmentLiteDTO update(final UUID id, final DepartmentUpsertDTO departmentUpsertDTO) {
        return departmentMapper.toLiteDTO(
                departmentService.update(
                        departmentMapper.toUpdateCommand(
                                id,
                                departmentUpsertDTO,
                                getOrganization(departmentUpsertDTO.organizationId()))));
    }

    private Organization getOrganization(final UUID organizationId) {
        if (organizationId == null) {
            throw new DomainException(DomainExceptionCode.ORGANIZATION_NOT_FOUND);
        }
        return organizationService.findById(organizationId);
    }

}
