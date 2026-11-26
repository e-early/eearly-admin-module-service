package si.result.project.eearly.port.department;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.department.command.CreateDepartmentCommand;
import si.result.project.eearly.model.department.command.UpdateDepartmentCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public Department findById(final UUID id) {
        return departmentRepository.findById(id).orElseThrow(() -> {
            log.error("Department with ID {} not found", id);
            return new DomainException(DomainExceptionCode.DEPARTMENT_NOT_FOUND);
        });
    }

    public Page<Department> findAll(final Specification<Department> specification, final Pageable pageable) {
        return departmentRepository.findAll(specification, pageable);
    }

    public Department create(final CreateDepartmentCommand command) {
        log.info("Creating department: {}", command);
        final var department = Department.create(command);
        final var savedDepartment = departmentRepository.save(department);
        log.debug("Department created with ID: {}", savedDepartment.getId());
        return savedDepartment;
    }

    public Department update(final UpdateDepartmentCommand command) {
        log.info("Updating department: {}", command);
        final var department = findById(command.getId());
        department.update(command);
        final var savedDepartment = departmentRepository.save(department);
        log.debug("Department updated with ID: {}", savedDepartment.getId());
        return savedDepartment;
    }
}
