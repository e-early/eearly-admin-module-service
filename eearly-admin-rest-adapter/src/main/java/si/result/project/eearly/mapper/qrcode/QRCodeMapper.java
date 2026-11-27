package si.result.project.eearly.mapper.qrcode;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.qrcode.QRCodeDTO;
import si.result.project.eearly.dto.qrcode.QRCodeLiteDTO;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.model.qrcode.command.CreateQRCodeCommand;

import java.time.OffsetDateTime;

@Mapper(uses = CaretakerMapper.class)
public interface QRCodeMapper {

    // The patient of a QR code is shown without the status values of the patient list.
    @Mapping(target = "patient.patientStatus", ignore = true)
    @Mapping(target = "patient.measurementStatus", ignore = true)
    QRCodeDTO toDTO(final QRCode qrCode);

    @Mapping(target = "patientId", source = "patient.id")
    QRCodeLiteDTO toLiteDTO(final QRCode qrCode);

    CreateQRCodeCommand toCreateCommand(final String token, final OffsetDateTime expiresAt,
                                        final String imageUrl, final Patient patient, final Caretaker caretaker, final String onboardingUrl);

}
