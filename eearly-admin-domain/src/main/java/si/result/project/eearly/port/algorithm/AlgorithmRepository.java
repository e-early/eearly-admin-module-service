package si.result.project.eearly.port.algorithm;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.algorithm.Algorithm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlgorithmRepository {

    Optional<Algorithm> findById(UUID id);

    Page<Algorithm> findAll(final Specification<Algorithm> specification, Pageable pageable);

    List<Algorithm> findAllById(final Iterable<UUID> id);

    Algorithm save(Algorithm algorithm);

}
