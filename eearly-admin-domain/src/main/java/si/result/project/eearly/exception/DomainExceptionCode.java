package si.result.project.eearly.exception;

import lombok.Getter;
import si.result.spring.boot.bricks.exception.ExceptionCode;
import si.result.spring.boot.bricks.exception.ExceptionReason;

@Getter
public enum DomainExceptionCode implements ExceptionCode {

  PATIENT_NOT_FOUND("Patient not found", ExceptionReason.ENTITY_NOT_FOUND),
  PATIENT_ID_NOT_PROVIDED("Patient ID must be provided", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_FIRSTNAME_NULL("Patient first name is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_FIRSTNAME_TOO_LONG("Patient first name is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_LASTNAME_NULL("Patient last name is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_LASTNAME_TOO_LONG("Patient last name is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_DATE_OF_BIRTH_NULL("Patient date of birth is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_DATE_OF_BIRTH_INVALID("Patient date of birth is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_GENDER_NULL("Patient gender is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_HEALTH_INSURANCE_ID_NULL("Patient health insurance id is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_HEALTH_INSURANCE_ID_FOUND("Patient health insurance id is already in the system", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_HEALTH_INSURANCE_ID_TOO_LONG("Patient insurance id is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_STREET_NULL("Patient street is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_STREET_TOO_LONG("Patient street is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_CITY_NULL("Patient city is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_CITY_TOO_LONG("Patient city is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_STATE_NULL("Patient state is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_STATE_TOO_LONG("Patient state is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_ZIP_NULL("Patient zip is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_COUNTRY_NULL("Patient country is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_COUNTRY_TOO_LONG("Patient country is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_EMAIL_NULL("Patient email is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_EMAIL_TOO_LONG("Patient email is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_EMAIL_FOUND("Patient email is already in the system", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_PHONE_NUMBER_NULL("Patient city is null", ExceptionReason.ILLEGAL_ARGUMENT),
  PATIENT_PHONE_NUMBER_TOO_LONG("Patient city is too long", ExceptionReason.ILLEGAL_ARGUMENT),

  CARETAKER_ID_NULL("Caretaker ID is null", ExceptionReason.ILLEGAL_ARGUMENT),
  CARETAKER_NOT_FOUND("Caretaker not found", ExceptionReason.ENTITY_NOT_FOUND),
  CARETAKER_FIRSTNAME_NULL("Caretaker first name is null", ExceptionReason.ILLEGAL_ARGUMENT),
  CARETAKER_FIRSTNAME_TOO_LONG("Caretaker first name is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  CARETAKER_LASTNAME_NULL("Caretaker last name is null", ExceptionReason.ILLEGAL_ARGUMENT),
  CARETAKER_LASTNAME_TOO_LONG("Caretaker last name is too long", ExceptionReason.ILLEGAL_ARGUMENT),
  CARETAKER_EMAIL_NULL("Caretaker email is null", ExceptionReason.ILLEGAL_ARGUMENT),
  CARETAKER_LANGUAGE_NULL("Caretaker language is null", ExceptionReason.ILLEGAL_ARGUMENT),

  MEASUREMENT_NOT_FOUND("Measurement not found", ExceptionReason.ENTITY_NOT_FOUND),
  MEASUREMENT_NAME_NULL("Measurement name is null", ExceptionReason.ILLEGAL_ARGUMENT),
  MEASUREMENT_TYPE_NULL("Measurement type is null", ExceptionReason.ILLEGAL_ARGUMENT),
  MEASUREMENT_CHART_TYPE_NULL("Measurement chart type is null", ExceptionReason.ILLEGAL_ARGUMENT),
  MEASUREMENT_EHR_OBSERVATION_ID_NULL("Measurement EHR observation ID is null", ExceptionReason.ILLEGAL_ARGUMENT),

  ANALYSIS_NOT_FOUND("Analysis not found", ExceptionReason.ENTITY_NOT_FOUND),
  ANALYSIS_NAME_NULL("Analysis name is null", ExceptionReason.ILLEGAL_ARGUMENT),
  ANALYSIS_STATE_NULL("Analysis state is null", ExceptionReason.ILLEGAL_ARGUMENT),
  ANALYSIS_PATIENT_NULL("Analysis patient is null", ExceptionReason.ILLEGAL_ARGUMENT),
  ANALYSIS_RESULT_DATA_MISSING("Analysis has no result data yet", ExceptionReason.ILLEGAL_STATE),
  ANALYSIS_DETECTION_NOT_FOUND("Detection not found for this analysis", ExceptionReason.ENTITY_NOT_FOUND),

  ALGORITHM_NOT_FOUND("Algorithm not found", ExceptionReason.ENTITY_NOT_FOUND),
  ALGORITHM_ID_NOT_PROVIDED("Algorithm ID must be provided", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_NAME_NULL("Algorithm name is null", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_SERVICE_ENDPOINT_MISSING("Algorithm service endpoint is missing", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_ENDPOINT_PATH_MISSING("Algorithm endpoint path is missing", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_CATEGORY("Algorithm category is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_STATUS("Algorithm status is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_INPUT_JSON("Algorithm input JSON is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_INPUT_SCHEMA("Algorithm input schema is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_OUTPUT_JSON("Algorithm output JSON is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_RUNNER_CONFIG("Algorithm runner config JSON is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_PRICING_MODEL("Algorithm pricing model is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_COST("Algorithm cost must not be negative", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_INVALID_CURRENCY("Algorithm currency is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ALGORITHM_NOT_ACTIVE("Algorithm must be ACTIVE to launch execution", ExceptionReason.ILLEGAL_STATE),
  ALGORITHM_SERVICE_CONFIGURATION_INVALID("Algorithm service health check is not configured", ExceptionReason.ILLEGAL_STATE),
  ALGORITHM_SERVICE_HEALTHCHECK_FAILED("Algorithm service health check failed", ExceptionReason.RUNTIME),

  ALGORITHM_EXECUTION_NOT_FOUND("Algorithm execution not found", ExceptionReason.ENTITY_NOT_FOUND),
  ALGORITHM_EXECUTION_INVALID_INPUT_PARAMETERS("Algorithm execution input parameters are invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  INVALID_EXECUTION_STATUS_TRANSITION("Invalid execution status transition", ExceptionReason.ILLEGAL_ARGUMENT),

  ALGORITHM_INPUT_PREPARATION_FAILED("Failed to prepare algorithm input payload", ExceptionReason.RUNTIME),

  MOBILE_SERVICE_REQUEST_FAILED("Request to Mobile Service failed", ExceptionReason.RUNTIME),

  ANALYSIS_FEEDBACK_PARAMETER_MISSING("Analysis feedback parameter is missing", ExceptionReason.ILLEGAL_ARGUMENT),
  ANALYSIS_FEEDBACK_PARAMETER_INVALID("Analysis feedback parameter is invalid", ExceptionReason.ILLEGAL_ARGUMENT),
  ANALYSIS_FEEDBACK_END_DATE_BEFORE_START_DATE("Analysis feedback end date is before start date", ExceptionReason.ILLEGAL_ARGUMENT),
  ANALYSIS_FEEDBACK_END_DATE_IN_FUTURE("Analysis feedback end date is in the future", ExceptionReason.ILLEGAL_ARGUMENT),
  ANALYSIS_FEEDBACK_DATE_RANGE_INCOMPLETE("Analysis feedback requires both startDate and endDate when either is provided", ExceptionReason.ILLEGAL_ARGUMENT),

  PATIENT_ID_NULL("Patient ID is null", ExceptionReason.ILLEGAL_ARGUMENT);


  private final String message;
  private final ExceptionReason reason;

  DomainExceptionCode(String message, ExceptionReason reason) {
    this.message = message;
    this.reason = reason;
  }
}
