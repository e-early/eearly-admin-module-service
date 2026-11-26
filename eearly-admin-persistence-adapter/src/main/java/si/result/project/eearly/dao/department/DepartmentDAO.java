package si.result.project.eearly.dao.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.port.department.DepartmentRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface DepartmentDAO extends DepartmentRepository,
        JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {}
