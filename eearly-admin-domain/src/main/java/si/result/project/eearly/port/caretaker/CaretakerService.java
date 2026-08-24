package si.result.project.eearly.port.caretaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.command.CreateCaretakerCommand;
import si.result.project.eearly.model.caretaker.command.UpdateCaretakerCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaretakerService {

    private final CaretakerRepository caretakerRepository;
    private final CaretakerClient caretakerClient;

    public Page<Caretaker> findAll(final Specification<Caretaker> specification, final Pageable pageable) {
        return caretakerRepository.findAll(specification, pageable);
    }

    public List<Caretaker> findAllById(final List<UUID> ids) {
        return caretakerRepository.findAllById(ids);
    }

    public Caretaker findById(final UUID id) {
        return caretakerRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Caretaker not found for id {}", id);
                    return new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND);
                });
    }

    public Caretaker findByKeycloakId(final UUID keycloakId) {
        return caretakerRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> {
                    log.error("Caretaker not found for keycloakId {}", keycloakId);
                    return new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND);
                });
    }

    public Caretaker create(CreateCaretakerCommand command) {
        log.info("Creating caretaker: {}", command);
        final var caretaker = Caretaker.create(command);
        log.debug("Caretaker created: {}", caretaker);
        final var savedCaretaker = caretakerRepository.save(caretaker);
        caretakerClient.upsertCaretaker(savedCaretaker);
        return savedCaretaker;
    }

    public Caretaker update(UpdateCaretakerCommand command) {
        log.info("Updating caretaker: {}", command);
        final var caretaker = findById(command.getId());
        caretaker.update(command);
        log.debug("Caretaker updated with ID: {}", caretaker.getId());
        final var savedCaretaker = caretakerRepository.save(caretaker);
        caretakerClient.upsertCaretaker(savedCaretaker);
        return savedCaretaker;
    }
}
