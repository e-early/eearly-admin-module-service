package si.result.project.eearly.dao.caretaker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.port.caretaker.CaretakerRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface CaretakerDAO extends CaretakerRepository,
        JpaRepository<Caretaker, UUID>, JpaSpecificationExecutor<Caretaker> {}
