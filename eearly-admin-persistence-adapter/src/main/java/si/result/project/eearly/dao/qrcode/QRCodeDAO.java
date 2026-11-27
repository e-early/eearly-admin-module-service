package si.result.project.eearly.dao.qrcode;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.port.qrcode.QRCodeRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface QRCodeDAO extends QRCodeRepository,
        JpaRepository<QRCode, UUID>, JpaSpecificationExecutor<QRCode> {}
