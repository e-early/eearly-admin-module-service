package si.result.project.eearly;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

class EEarlyAdminApplicationTest {

    @Test
    void isASpringBootApplication() {
        assertThat(EEarlyAdminApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
    }

    @Test
    void scansTheWholeResultPackageTree() {
        ComponentScan scan = EEarlyAdminApplication.class.getAnnotation(ComponentScan.class);
        assertThat(scan).isNotNull();
        assertThat(scan.basePackages()).containsExactly("si.result");
    }
}
