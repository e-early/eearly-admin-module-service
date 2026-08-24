package si.result.project.eearly.port.caretaker;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.caretaker.Caretaker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaretakerRepository {

    Page<Caretaker> findAll(final Specification<Caretaker> specification, final Pageable pageable);

    List<Caretaker> findAllById(Iterable<UUID> ids);

    Optional<Caretaker> findById(UUID id);

    Optional<Caretaker> findByKeycloakId(final UUID keycloakId);

    Caretaker save(Caretaker caretaker);

}
