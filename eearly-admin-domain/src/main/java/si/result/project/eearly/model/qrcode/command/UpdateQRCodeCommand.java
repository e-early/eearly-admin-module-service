package si.result.project.eearly.model.qrcode.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import si.result.project.eearly.model.qrcode.QRCodeStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Replaces the values of a QR code. Holds the code's token, so it deliberately has no {@code toString}: never log it.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class UpdateQRCodeCommand {

    private final UUID id;
    private final String token;
    private final OffsetDateTime expiresAt;
    private final String imageUrl;
    private final QRCodeStatus status;
    private final Boolean mobileAppConfigured;

}
