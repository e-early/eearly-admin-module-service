package si.result.project.eearly.model.qrcode;

/**
 * Where a QR code is in its life: {@code ACTIVE} until the app is configured with it ({@code USED}); {@code EXPIRED}
 * and {@code REVOKED} are reserved for the expiry and revocation of a code. Whether a code can still be used also
 * depends on its expiry time, see {@code QRCodeUtilityService#isQrTokenValid}.
 */
public enum QRCodeStatus {
    ACTIVE,
    USED,
    EXPIRED,
    REVOKED
}
