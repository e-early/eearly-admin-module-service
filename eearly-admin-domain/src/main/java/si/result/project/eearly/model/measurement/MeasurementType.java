package si.result.project.eearly.model.measurement;

public enum MeasurementType {
    BLOOD_PRESSURE,
    BLOOD_PRESSURE_SYSTOLIC,
    BLOOD_PRESSURE_DIASTOLIC,
    HEART_RATE,
    WEIGHT,
    HEIGHT,
    BODY_TEMPERATURE,
    OXYGEN_SATURATION,
    BLOOD_GLUCOSE,
    DAILY_STEPS,

    BODY_FAT_PERCENTAGE,
    MUSCLE_MASS,
    BODY_WATER_PERCENTAGE,
    SLEEP_DURATION,
    SLEEP_QUALITY,
    ECG,
    BLOOD_PRESSURE_VARIABILITY,
    PEAK_FLOW,

    SKIN_TEMPERATURE,
    RESPIRATORY_RATE,
    HEART_RATE_VARIABILITY,
    STRESS_LEVEL,
    GLUCOSE_VARIABILITY,
    BASIC_VISION_SCREENING,
    // Body composition from a smart scale weigh-in, alongside BODY_FAT_PERCENTAGE and
    // BODY_WATER_PERCENTAGE above.
    BODY_MASS_INDEX,
    FAT_FREE_MASS,
    FAT_MASS,
    SKELETAL_MUSCLE_MASS,
    BASAL_METABOLIC_RATE,
    // Shares of the same weigh-in's body weight.
    FAT_FREE_MASS_PERCENTAGE,
    SKELETAL_MUSCLE_MASS_PERCENTAGE,
    // Total body water in kg, from the same weigh-in.
    BODY_WATER_MASS
}
