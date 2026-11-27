package si.result.project.eearly.model.qrcode.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class ConfigureQRCodeCommand {

    private final UUID id;
    private final boolean mobileAppConfigured;

}
