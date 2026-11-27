package si.result.project.eearly.facade.healthcondition;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.healthcondition.HealthConditionDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionLiteDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionMinimalDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionUpsertDTO;
import si.result.project.eearly.mapper.healthcondition.HealthConditionMapper;
import si.result.project.eearly.port.healthcondition.HealthConditionService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;

import java.util.UUID;

@Facade
@RequiredArgsConstructor
public class HealthConditionServiceFacade {
    private final HealthConditionService healthConditionService;
    private final HealthConditionMapper healthConditionMapper;

    @Transactional(readOnly = true)
    public PagedModel<HealthConditionLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                healthConditionService.findAll(filter.toSpecification(), pageable)
                        .map(healthConditionMapper::toLiteDTO)
        );
    }

    @Transactional(readOnly = true)
    public HealthConditionDTO getById(final UUID id) {
        return healthConditionMapper.toDTO(healthConditionService.findById(id));
    }

    @Transactional()
    public HealthConditionMinimalDTO create(final HealthConditionUpsertDTO healthConditionUpsertDTO) {
        return healthConditionMapper.toMinimalDTO(
                healthConditionService.create(
                        healthConditionMapper.toCreateCommand(healthConditionUpsertDTO)
                )
        );
    }
}
