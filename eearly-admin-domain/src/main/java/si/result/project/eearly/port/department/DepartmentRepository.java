package si.result.project.eearly.port.department;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.department.Department;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository {

    Page<Department> findAll(Specification<Department> specification, Pageable pageable);

    Optional<Department> findById(UUID id);

    Department save(Department department);

}
