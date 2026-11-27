package si.result.project.eearly.util.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.exception.DomainException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.UUID;

/**
 * Draws the QR code image of a QR code: a PNG, returned as a {@code data:image/png;base64,} URI. The code encodes
 * the onboarding URL and the token; both are credentials and are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QRCodeImageService {

    /**
     * Resource location of the logo drawn in the middle of the code (the logo of the e-mails). Blank, or a logo that
     * does not exist, gives a code without a logo.
     */
    @Value("${email.logo:classpath:images/email-logo.png}")
    private String logoPath;

    /**
     * Fixed app-link page the QR code points at, e.g. {@code https://app.example.org/onboarding}. The app opens
     * links on that domain directly; without the app, the page sends the browser to the store. Blank keeps
     * the old format (the instance's own onboarding URL), which app versions before the link handler need.
     */
    @Value("${qrcode.link-base-url:}")
    private String linkBaseUrl;

    private final ResourceLoader resourceLoader;

    private static final String QR_CODE_URL = "%s?token=%s";
    private static final String QR_CODE_LINK_URL = "%s?u=%s&token=%s";
    private static final int QR_CODE_SIZE = 300;
    private static final String QR_CODE_FORMAT = "PNG";
    private static final String QR_TEXT_FONT = "Arial";

    public String generateQRCodeImage(UUID patientId, String onboardingUrl, String token, QRCodeGenerateType qrCodeGenerateType) {
        return switch (qrCodeGenerateType) {
            case IMAGE -> generateQRCodeImageWithPngOverlay(patientId, onboardingUrl, token, logoPath);
            case TEXT -> generateQRCodeImageWithText(patientId, onboardingUrl, token, "EEARLY");
        };
    }

    public String generateQRCodeImageWithText(UUID patientId, String onboardingUrl, String token, String centerText) {
        try {
            String qrContent = buildQrContent(onboardingUrl, token);

            BufferedImage qrImage = generateBaseQRCode(qrContent);

            BufferedImage finalImage = addTextOverlay(qrImage, centerText);

            log.info("Generated QR code with text for patient: {}", patientId);
            return toDataUri(finalImage);

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code with text for patient: {}", patientId, e);
            throw new DomainException(DomainExceptionCode.QR_CODE_GENERATION_FAILED);
        }
    }

    public String generateQRCodeImageWithPngOverlay(UUID patientId, String onboardingUrl, String token, String imagePath) {
        try {
            String qrContent = buildQrContent(onboardingUrl, token);

            BufferedImage qrImage = generateBaseQRCode(qrContent);
            BufferedImage overlayImage = loadPngImage(imagePath);
            BufferedImage finalImage = overlayImage == null ? qrImage : addImageOverlay(qrImage, overlayImage);

            log.info("Generated QR code {} for patient: {}", overlayImage == null ? "without logo" : "with logo", patientId);
            return toDataUri(finalImage);

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code with PNG overlay for patient: {}", patientId, e);
            throw new DomainException(DomainExceptionCode.QR_CODE_GENERATION_FAILED);
        }
    }


    String buildQrContent(String onboardingUrl, String token) {
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) {
            return String.format(QR_CODE_URL, onboardingUrl, token);
        }
        return String.format(QR_CODE_LINK_URL, linkBaseUrl,
            URLEncoder.encode(onboardingUrl, StandardCharsets.UTF_8),
            URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    private static String toDataUri(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, QR_CODE_FORMAT, outputStream);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private BufferedImage generateBaseQRCode(String content) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        EnumMap<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);

        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE,
                QR_CODE_SIZE, QR_CODE_SIZE, hints);

        BufferedImage qrImage = new BufferedImage(QR_CODE_SIZE, QR_CODE_SIZE,
                BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < QR_CODE_SIZE; x++) {
            for (int y = 0; y < QR_CODE_SIZE; y++) {
                qrImage.setRGB(x, y, bitMatrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        return qrImage;
    }

    private BufferedImage addTextOverlay(BufferedImage qrImage, String text) {
        BufferedImage combined = new BufferedImage(QR_CODE_SIZE, QR_CODE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = combined.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2d.drawImage(qrImage, 0, 0, null);

        int textAreaSize = QR_CODE_SIZE / 4; // Text area will be 1/4 of QR code size
        int textAreaX = (QR_CODE_SIZE - textAreaSize) / 2;
        int textAreaY = (QR_CODE_SIZE - textAreaSize) / 2;

        int borderSize = 4;
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(textAreaX - borderSize, textAreaY - borderSize,
                textAreaSize + (borderSize * 2), textAreaSize + (borderSize * 2), 12, 12);

        g2d.setColor(new Color(230, 230, 230));
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRoundRect(textAreaX - borderSize, textAreaY - borderSize,
                textAreaSize + (borderSize * 2), textAreaSize + (borderSize * 2), 12, 12);

        Font font = calculateOptimalFont(text, textAreaSize - 10, g2d);
        g2d.setFont(font);
        g2d.setColor(Color.BLACK);

        FontMetrics fm = g2d.getFontMetrics(font);
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        int textX = (QR_CODE_SIZE - textWidth) / 2;
        int textY = (QR_CODE_SIZE - textHeight) / 2 + fm.getAscent();

        g2d.drawString(text, textX, textY);

        g2d.dispose();
        return combined;
    }

    private BufferedImage addImageOverlay(BufferedImage qrImage, BufferedImage overlayImage) {
        BufferedImage combined = new BufferedImage(QR_CODE_SIZE, QR_CODE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = combined.createGraphics();

        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Draw the QR code as background
        g2d.drawImage(qrImage, 0, 0, null);

        // Calculate overlay dimensions (1/4 of QR code size)
        int overlaySize = QR_CODE_SIZE / 4;
        int overlayX = (QR_CODE_SIZE - overlaySize) / 2;
        int overlayY = (QR_CODE_SIZE - overlaySize) / 2;

        // Create a white background with border for the overlay
        int borderSize = 6;
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(overlayX - borderSize, overlayY - borderSize,
                overlaySize + (borderSize * 2), overlaySize + (borderSize * 2), 15, 15);

        // Add a subtle border
        g2d.setColor(new Color(220, 220, 220));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(overlayX - borderSize, overlayY - borderSize,
                overlaySize + (borderSize * 2), overlaySize + (borderSize * 2), 15, 15);

        // Scale and draw the overlay image
        BufferedImage scaledOverlay = scaleImageSmoothly(overlayImage, overlaySize, overlaySize);
        g2d.drawImage(scaledOverlay, overlayX, overlayY, null);

        g2d.dispose();
        return combined;
    }

    /** The logo image, or null when no logo is configured or it does not exist (the code is then drawn without it). */
    private BufferedImage loadPngImage(String imagePath) throws IOException {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        Resource resource = resourceLoader.getResource(imagePath);
        if (!resource.exists()) {
            log.warn("QR code logo not found at {}, generating the code without it", imagePath);
            return null;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                log.error("Failed to load image from path: {}", imagePath);
                throw new DomainException(DomainExceptionCode.QR_CODE_GENERATION_FAILED);
            }
            return image;
        }
    }

    private BufferedImage scaleImageSmoothly(BufferedImage originalImage, int targetWidth, int targetHeight) {
        // Create a new image with the target dimensions
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();

        // Enable high-quality scaling
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Calculate scaling to maintain aspect ratio
        double scaleX = (double) targetWidth / originalImage.getWidth();
        double scaleY = (double) targetHeight / originalImage.getHeight();
        double scale = Math.min(scaleX, scaleY);

        int scaledWidth = (int) (originalImage.getWidth() * scale);
        int scaledHeight = (int) (originalImage.getHeight() * scale);

        // Center the image
        int x = (targetWidth - scaledWidth) / 2;
        int y = (targetHeight - scaledHeight) / 2;

        // Draw the scaled image
        g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return scaledImage;
    }

    private Font calculateOptimalFont(String text, int maxWidth, Graphics2D g2d) {
        int fontSize = 20;
        Font font = new Font(QR_TEXT_FONT, Font.BOLD, fontSize);
        FontMetrics fm = g2d.getFontMetrics(font);

        while (fm.stringWidth(text) < maxWidth && fontSize < 30) {
            fontSize++;
            font = new Font(QR_TEXT_FONT, Font.BOLD, fontSize);
            fm = g2d.getFontMetrics(font);
        }

        while (fm.stringWidth(text) > maxWidth && fontSize > 8) {
            fontSize--;
            font = new Font(QR_TEXT_FONT, Font.BOLD, fontSize);
            fm = g2d.getFontMetrics(font);
        }

        return font;
    }

}
