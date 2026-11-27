package si.result.project.eearly.config.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import si.result.project.eearly.port.email.EmailProperties;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * E-mail settings and the thread pool the e-mails are sent on ({@code @Async("emailTaskExecutor")}).
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean(name = "emailTaskExecutor")
    public AsyncTaskExecutor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
