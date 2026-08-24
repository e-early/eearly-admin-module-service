package si.result.project.eearly.mapper.caretaker;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerLiteDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.command.CreateCaretakerCommand;
import si.result.project.eearly.model.caretaker.command.UpdateCaretakerCommand;

import java.util.UUID;

@Mapper
public interface CaretakerMapper {

    CaretakerDTO toDTO(final Caretaker caretaker);

    CaretakerLiteDTO toLiteDTO(final Caretaker caretaker);

    CaretakerUpsertDTO toUpsertDTO(final Caretaker caretaker);

    CreateCaretakerCommand toCreateCommand(final CaretakerUpsertDTO dto);

    @Mapping(target = "id", source = "id")
    UpdateCaretakerCommand toUpdateCommand(final UUID id, final CaretakerUpsertDTO dto);
}
