package si.result.project.eearly.resource.qrcode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.qrcode.QRCodeDTO;
import si.result.project.eearly.dto.qrcode.QRCodeGenerateDTO;
import si.result.project.eearly.dto.qrcode.QRCodeLiteDTO;
import si.result.project.eearly.dto.qrcode.QRCodeSendEmailDTO;
import si.result.project.eearly.facade.qrcode.QRCodeServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "QR Code Controller")
@RestController
@RequestMapping("/api/v1/qrcodes")
@RequiredArgsConstructor
public class QRCodeController {

    private final QRCodeServiceFacade qrCodeServiceFacade;

    @Operation(description = "QR codes of the patients assigned to the authenticated caretaker")
    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<QRCodeLiteDTO>>> getPage(
            @ParameterObject @PageableDefault final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(qrCodeServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<QRCodeDTO>> getById(
            @PathVariable(name = "id") final UUID uuid) {
        return new ResponseDTO<>(qrCodeServiceFacade.getById(uuid)).ok();
    }

    @GetMapping(value = "/patient/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<QRCodeDTO>> getByPatientId(
            @PathVariable(name = "id") final UUID uuid) {
        return new ResponseDTO<>(qrCodeServiceFacade.getByPatientId(uuid)).ok();
    }

    @Operation(description = "Create the QR code of a patient assigned to the authenticated caretaker")
    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<QRCodeLiteDTO>> createQRCode(
            @RequestBody final QRCodeGenerateDTO qrCodeGenerateDTO) {
        return new ResponseDTO<>(qrCodeServiceFacade.create(qrCodeGenerateDTO)).created();
    }

    @Operation(description = "Send QR code to the patient's e-mail address, in the patient's language")
    @PostMapping(value = "/send-email", produces = "application/json")
    public ResponseEntity<ResponseDTO<String>> sendQRCodeByEmail(
            @RequestBody final QRCodeSendEmailDTO qrCodeSendEmailDTO) {
        qrCodeServiceFacade.sendQRCodeByEmailAsync(qrCodeSendEmailDTO);
        return new ResponseDTO<>("QR code email is being sent to patient").ok();
    }
}
