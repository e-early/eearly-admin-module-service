package si.result.project.eearly.mapper.algorithm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import si.result.project.eearly.dto.algorithm.AlgorithmDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmStatusDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.AlgorithmCategoryType;
import si.result.project.eearly.model.algorithm.AlgorithmPricingModelType;
import si.result.project.eearly.model.algorithm.AlgorithmStatusType;
import si.result.project.eearly.model.algorithm.command.CreateAlgorithmCommand;
import si.result.project.eearly.model.algorithm.command.UpdateAlgorithmCommand;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.exception.DomainException;

import java.math.BigDecimal;
import java.util.List;

@Mapper(uses = {si.result.project.eearly.mapper.patient.PatientMapper.class})
public interface AlgorithmMapper {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    AlgorithmDTO toDTO(final Algorithm algorithm);

    AlgorithmUpsertDTO toUpsertDTO(final Algorithm algorithm);

    AlgorithmStatusDTO toStatusDTO(final Algorithm algorithm);

    CreateAlgorithmCommand toCreateCommand(final AlgorithmUpsertDTO dto,
                                           final List<Patient> patients, final List<Measurement> measurements);

    UpdateAlgorithmCommand toUpdateCommand(final AlgorithmUpsertDTO dto,
                                           final List<Patient> patients, final List<Measurement> measurements);

    default void validateJson(String json, DomainExceptionCode code) {
        if (json == null) return;
        try {
            OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new DomainException(code);
        }
    }

    default void validateCost(BigDecimal cost) {
        if (cost != null && cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_INVALID_COST);
        }
    }

    @AfterMapping
    default void validateAfterCreate(@MappingTarget CreateAlgorithmCommand command) {
        validateJson(command.inputSchema(), DomainExceptionCode.ALGORITHM_INVALID_INPUT_JSON);
        validateJson(command.outputSchema(), DomainExceptionCode.ALGORITHM_INVALID_OUTPUT_JSON);
        validateJson(command.runnerConfig(), DomainExceptionCode.ALGORITHM_INVALID_RUNNER_CONFIG);
        validateCost(command.costPerExecution());
    }

    @AfterMapping
    default void validateAfterUpdate(@MappingTarget UpdateAlgorithmCommand command) {
        validateJson(command.inputSchema(), DomainExceptionCode.ALGORITHM_INVALID_INPUT_JSON);
        validateJson(command.outputSchema(), DomainExceptionCode.ALGORITHM_INVALID_OUTPUT_JSON);
        validateJson(command.runnerConfig(), DomainExceptionCode.ALGORITHM_INVALID_RUNNER_CONFIG);
        validateCost(command.costPerExecution());
    }

    default <E extends Enum<E>> E mapEnum(
            String value,
            Class<E> enumClass,
            DomainExceptionCode errorCode
    ) {
        if (value == null) {
            return null;
        }

        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new DomainException(errorCode);
        }
    }

    default AlgorithmCategoryType mapCategory(String category) {
        return mapEnum(
                category,
                AlgorithmCategoryType.class,
                DomainExceptionCode.ALGORITHM_INVALID_CATEGORY
        );
    }

    default AlgorithmStatusType mapStatus(String status) {
        return mapEnum(
                status,
                AlgorithmStatusType.class,
                DomainExceptionCode.ALGORITHM_INVALID_STATUS
        );
    }

    default AlgorithmPricingModelType mapPricingModel(String pricingModel) {
        return mapEnum(
                pricingModel,
                AlgorithmPricingModelType.class,
                DomainExceptionCode.ALGORITHM_INVALID_PRICING_MODEL
        );
    }

    default java.util.Currency mapCurrency(String currency) {
        if (currency == null) {
            return null;
        }

        try {
            return java.util.Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_INVALID_CURRENCY);
        }
    }
}
