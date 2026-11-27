package si.result.project.eearly.model.mobile;

/**
 * Answer of the mobile service to a request for a new onboarding link. The link is a single-use credential, so it
 * deliberately has no useful {@code toString}: never log it.
 */
public record MobileOnboardingLinkResponse(String onboardingUrl) {

    @Override
    public String toString() {
        return "MobileOnboardingLinkResponse";
    }
}
