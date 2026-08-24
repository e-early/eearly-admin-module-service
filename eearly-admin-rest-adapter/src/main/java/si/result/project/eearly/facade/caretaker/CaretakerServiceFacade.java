package si.result.project.eearly.facade.caretaker;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerLiteDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;

import java.util.UUID;

@Facade
@RequiredArgsConstructor
public class CaretakerServiceFacade {
    private final CaretakerService caretakerService;
    private final CaretakerMapper caretakerMapper;

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
                caretakerService.create(caretakerMapper.toCreateCommand(caretakerUpsertDTO)));
    }

    @Transactional()
    public CaretakerDTO update(final UUID uuid, final CaretakerUpsertDTO caretakerUpsertDTO) {
        return caretakerMapper.toDTO(
                caretakerService.update(caretakerMapper.toUpdateCommand(uuid, caretakerUpsertDTO)));
    }
}
