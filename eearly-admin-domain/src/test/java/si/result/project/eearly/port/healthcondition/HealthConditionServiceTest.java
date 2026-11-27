package si.result.project.eearly.port.healthcondition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.healthcondition.HealthCondition;
import si.result.project.eearly.model.healthcondition.command.CreateHealthConditionCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthConditionServiceTest {

    @Mock
    private HealthConditionRepository healthConditionRepository;

    @InjectMocks
    private HealthConditionService healthConditionService;

    @Test
    void shouldCreateHealthCondition() {
        // Given
        CreateHealthConditionCommand command = new CreateHealthConditionCommand("Test Condition");
        when(healthConditionRepository.save(any(HealthCondition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        HealthCondition result = healthConditionService.create(command);

        // Then
        ArgumentCaptor<HealthCondition> saved = ArgumentCaptor.forClass(HealthCondition.class);
        verify(healthConditionRepository).save(saved.capture());
        assertSame(result, saved.getValue());
        assertEquals("Test Condition", result.getName());
    }

    @Test
    void shouldRejectHealthConditionWithoutName() {
        CreateHealthConditionCommand command = new CreateHealthConditionCommand(null);

        DomainException exception = assertThrows(DomainException.class, () -> healthConditionService.create(command));

        assertEquals(DomainExceptionCode.HEALTH_CONDITION_NAME_NULL, exception.getCode());
        verify(healthConditionRepository, never()).save(any());
    }

    @Test
    void shouldFindHealthConditionById() {
        UUID id = UUID.randomUUID();
        HealthCondition healthCondition = HealthCondition.create(new CreateHealthConditionCommand("Test Condition"));
        when(healthConditionRepository.findById(id)).thenReturn(Optional.of(healthCondition));

        assertSame(healthCondition, healthConditionService.findById(id));
    }

    @Test
    void shouldThrowNotFound_whenHealthConditionDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(healthConditionRepository.findById(id)).thenReturn(Optional.empty());

        DomainException exception = assertThrows(DomainException.class, () -> healthConditionService.findById(id));

        assertEquals(DomainExceptionCode.HEALTH_CONDITION_NOT_FOUND, exception.getCode());
    }

    @Test
    void shouldFindAllByIdFromTheRepository() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<HealthCondition> found = List.of(
                HealthCondition.create(new CreateHealthConditionCommand("First Condition")));
        when(healthConditionRepository.findAllById(ids)).thenReturn(found);

        assertSame(found, healthConditionService.findAllById(ids));
    }
}
