package si.result.project.eearly.model.ehr;

import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;

public record EhrRecord(
    String observationId,
    String measurementType,
    String unit,
    Double magnitude,
    String batchId,
    ZonedDateTime measurementTime
) {
  public String getLowercasedMeasurementType() {
    return StringUtils.lowerCase(measurementType);
  }
}
