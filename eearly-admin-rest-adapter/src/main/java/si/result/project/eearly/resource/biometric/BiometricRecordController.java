package si.result.project.eearly.resource.biometric;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.biometric.BiometricRecordBatchDTO;
import si.result.project.eearly.dto.biometric.BiometricRecordTimeSeriesDTO;
import si.result.project.eearly.facade.biometric.BiometricRecordServiceFacade;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.List;
import java.util.UUID;

@Tag(name = "Biometric Record Controller")
@RestController
@RequestMapping("/api/v1/biometric-records")
@RequiredArgsConstructor
public class BiometricRecordController {

  private final BiometricRecordServiceFacade biometricRecordServiceFacade;

  @Operation(description = "Get biometric record stream for given patient and measurement")
  @GetMapping(value = "/{patientId}/measurement/{measurement}", produces = "application/json")
  public ResponseEntity<ResponseDTO<BiometricRecordTimeSeriesDTO>> getRecords(
          @PathVariable(name = "patientId") final UUID patientId,
          @Schema(example = "BLOOD_PRESSURE", implementation = BiometricMeasurementType.class) @PathVariable(name = "measurement") final String measurement,
          @RequestParam(name = "startDateTime", required = false) final String startDateTime,
          @RequestParam(name = "endDateTime", required = false) final String endDateTime,
          @RequestParam(name = "aggregationLevel", required = false, defaultValue = "NONE") final BiometricDataAggregationLevel aggregationLevel) {

    return new ResponseDTO<>(biometricRecordServiceFacade.getBiometricRecordTimeSeries(patientId, measurement, startDateTime, endDateTime, ObjectUtils.defaultIfNull(aggregationLevel, BiometricDataAggregationLevel.NONE))).ok();
  }

  @Operation(description = "Get biometric record streams for given patient and measurement types")
  @GetMapping(value = "/{patientId}", produces = "application/json")
  public ResponseEntity<ResponseDTO<BiometricRecordBatchDTO>> getRecordsBatch(
          @PathVariable(name = "patientId") final UUID patientId,
          @RequestParam(name = "measurementTypes") final List<String> measurementTypes,
          @RequestParam(name = "startDateTime", required = false) final String startDateTime,
          @RequestParam(name = "endDateTime", required = false) final String endDateTime,
          @RequestParam(name = "aggregationLevel", required = false, defaultValue = "NONE") final BiometricDataAggregationLevel aggregationLevel) {

    return new ResponseDTO<>(biometricRecordServiceFacade.getBiometricRecordTimeSeriesBatch(
            patientId,
            measurementTypes,
            startDateTime,
            endDateTime,
            ObjectUtils.defaultIfNull(aggregationLevel, BiometricDataAggregationLevel.NONE)
    )).ok();
  }
}
