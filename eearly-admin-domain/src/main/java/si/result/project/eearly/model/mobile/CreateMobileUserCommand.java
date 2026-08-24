package si.result.project.eearly.model.mobile;

public record CreateMobileUserCommand(
    String firstName,
    String lastName,
    String email
) {

}
