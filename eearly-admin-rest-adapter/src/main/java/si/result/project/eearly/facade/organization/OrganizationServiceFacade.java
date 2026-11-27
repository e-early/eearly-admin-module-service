package si.result.project.eearly.facade.organization;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.organization.OrganizationDTO;
import si.result.project.eearly.dto.organization.OrganizationLiteDTO;
import si.result.project.eearly.dto.organization.OrganizationUpsertDTO;
import si.result.project.eearly.mapper.organization.OrganizationMapper;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;

import java.util.UUID;

@Facade
@RequiredArgsConstructor
public class OrganizationServiceFacade {
    private final OrganizationService organizationService;
    private final OrganizationMapper organizationMapper;

    @Transactional(readOnly = true)
    public PagedModel<OrganizationLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                organizationService.findAll(filter.toSpecification(), pageable)
                        .map(organizationMapper::toLiteDTO)
        );
    }

    @Transactional(readOnly = true)
    public OrganizationDTO getById(final UUID id) {
        return organizationMapper.toDTO(organizationService.findById(id));
    }

    @Transactional()
    public OrganizationUpsertDTO create(final OrganizationUpsertDTO organizationUpsertDTO) {
        return organizationMapper.toUpsertDTO(
                organizationService.create(
                        organizationMapper.toCreateCommand(organizationUpsertDTO)));
    }
}
