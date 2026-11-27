package si.result.project.eearly.util.qrcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.exception.DomainException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class QRCodeImageServiceTest {

  private static final String ONBOARDING_URL =
      "https://mobile.example.org/api/v1/onboarding/52378678-4cad-4f7b-893e-9e129e47824a";
  private static final String TOKEN = "0b6c1f3e-77a1-4f0e-9a52-1d6f6a8e2b11-1700000000000";
  private static final String DATA_URI_PREFIX = "data:image/png;base64,";

  private final QRCodeImageService service = new QRCodeImageService(new DefaultResourceLoader());
  private final UUID patientId = UUID.randomUUID();

  @TempDir
  Path tempDir;

  @BeforeEach
  void blankLinkBase() {
    ReflectionTestUtils.setField(service, "linkBaseUrl", "");
  }

  @Test
  void blankLinkBaseKeepsOldFormat() {
    ReflectionTestUtils.setField(service, "linkBaseUrl", "");
    assertEquals(ONBOARDING_URL + "?token=abc", service.buildQrContent(ONBOARDING_URL, "abc"));
  }

  @Test
  void linkBaseWrapsOnboardingUrl() {
    ReflectionTestUtils.setField(service, "linkBaseUrl", "https://app.example.org/onboarding");
    assertEquals(
        "https://app.example.org/onboarding?u=https%3A%2F%2Fmobile.example.org%2Fapi%2Fv1%2Fonboarding%2F52378678-4cad-4f7b-893e-9e129e47824a&token=a%2Bb",
        service.buildQrContent(ONBOARDING_URL, "a+b"));
  }

  @Test
  void theCodeWithLogoEncodesTheOnboardingUrlAndTheToken() throws Exception {
    String logo = logoFile().toUri().toString();

    String image = service.generateQRCodeImageWithPngOverlay(patientId, ONBOARDING_URL, TOKEN, logo);

    assertEquals(ONBOARDING_URL + "?token=" + TOKEN, decode(image));
  }

  @Test
  void theConfiguredLogoIsUsedForTheImageType() throws Exception {
    ReflectionTestUtils.setField(service, "logoPath", logoFile().toUri().toString());

    String image = service.generateQRCodeImage(patientId, ONBOARDING_URL, TOKEN, QRCodeGenerateType.IMAGE);

    assertEquals(ONBOARDING_URL + "?token=" + TOKEN, decode(image));
  }

  @Test
  void theAppLinkFormatEncodesBothValues() throws Exception {
    ReflectionTestUtils.setField(service, "linkBaseUrl", "https://app.example.org/onboarding");

    String image = service.generateQRCodeImageWithPngOverlay(patientId, ONBOARDING_URL, TOKEN, logoFile().toUri().toString());

    assertEquals(service.buildQrContent(ONBOARDING_URL, TOKEN), decode(image));
    assertTrue(decode(image).startsWith("https://app.example.org/onboarding?u="));
  }

  @Test
  void theCodeWithTextEncodesTheOnboardingUrlAndTheToken() throws Exception {
    String image = service.generateQRCodeImage(patientId, ONBOARDING_URL, TOKEN, QRCodeGenerateType.TEXT);

    assertEquals(ONBOARDING_URL + "?token=" + TOKEN, decode(image));
  }

  @Test
  void aBlankLogoGivesACodeWithoutLogo() throws Exception {
    ReflectionTestUtils.setField(service, "logoPath", "");

    String image = service.generateQRCodeImage(patientId, ONBOARDING_URL, TOKEN, QRCodeGenerateType.IMAGE);

    assertEquals(ONBOARDING_URL + "?token=" + TOKEN, decode(image));
  }

  @Test
  void aMissingLogoGivesACodeWithoutLogo() throws Exception {
    String missing = tempDir.resolve("missing.png").toUri().toString();

    String image = service.generateQRCodeImageWithPngOverlay(patientId, ONBOARDING_URL, TOKEN, missing);

    assertEquals(ONBOARDING_URL + "?token=" + TOKEN, decode(image));
  }

  @Test
  void aLogoThatIsNoImageFails() throws Exception {
    Path notAnImage = tempDir.resolve("logo.png");
    Files.writeString(notAnImage, "this is not a PNG");

    DomainException exception = assertThrows(DomainException.class,
        () -> service.generateQRCodeImageWithPngOverlay(patientId, ONBOARDING_URL, TOKEN, notAnImage.toUri().toString()));

    assertEquals(DomainExceptionCode.QR_CODE_GENERATION_FAILED, exception.getCode());
  }

  @Test
  void contentThatIsTooLongFails() {
    String tooLong = "https://mobile.example.org/" + "a".repeat(5000);

    DomainException exception = assertThrows(DomainException.class,
        () -> service.generateQRCodeImageWithPngOverlay(patientId, tooLong, TOKEN, ""));

    assertEquals(DomainExceptionCode.QR_CODE_GENERATION_FAILED, exception.getCode());
  }

  @Test
  void theImageIsAPngDataUri() throws Exception {
    String image = service.generateQRCodeImageWithText(patientId, ONBOARDING_URL, TOKEN, "TEST");

    assertTrue(image.startsWith(DATA_URI_PREFIX));
    BufferedImage png = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image.substring(DATA_URI_PREFIX.length()))));
    assertEquals(300, png.getWidth());
    assertEquals(300, png.getHeight());
  }

  @Test
  void generatingACodeLogsNeitherTheTokenNorTheOnboardingUrl(CapturedOutput output) throws Exception {
    service.generateQRCodeImageWithPngOverlay(patientId, ONBOARDING_URL, TOKEN, logoFile().toUri().toString());
    service.generateQRCodeImageWithText(patientId, ONBOARDING_URL, TOKEN, "TEST");
    service.generateQRCodeImageWithPngOverlay(patientId, ONBOARDING_URL, TOKEN, "");
    assertThrows(DomainException.class,
        () -> service.generateQRCodeImageWithPngOverlay(patientId, "https://mobile.example.org/" + "a".repeat(5000), TOKEN, ""));

    assertFalse(output.getAll().contains(TOKEN));
    assertFalse(output.getAll().contains("0b6c1f3e"));
    assertFalse(output.getAll().contains("52378678-4cad-4f7b-893e-9e129e47824a"));
  }

  private Path logoFile() throws Exception {
    BufferedImage logo = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = logo.createGraphics();
    g.setColor(new Color(0, 120, 200));
    g.fillOval(4, 4, 56, 56);
    g.dispose();
    Path file = tempDir.resolve("logo-" + UUID.randomUUID() + ".png");
    ImageIO.write(logo, "PNG", file.toFile());
    return file;
  }

  private static String decode(String dataUri) throws Exception {
    byte[] png = Base64.getDecoder().decode(dataUri.substring(DATA_URI_PREFIX.length()));
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    BinaryBitmap bitmap = new BinaryBitmap(
        new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
    return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
  }
}
