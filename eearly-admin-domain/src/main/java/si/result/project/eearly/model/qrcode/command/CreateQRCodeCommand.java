package si.result.project.eearly.model.qrcode.command;

import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;

import java.time.OffsetDateTime;

/**
 * Creates a QR code. Holds the code's token, so it deliberately has no useful {@code toString}: never log it.
 */
public record CreateQRCodeCommand(
    String token,
    OffsetDateTime expiresAt,
    String imageUrl,
    Patient patient,
    Caretaker caretaker,
    String onboardingUrl) {

    @Override
    public String toString() {
        return "CreateQRCodeCommand";
    }
}
