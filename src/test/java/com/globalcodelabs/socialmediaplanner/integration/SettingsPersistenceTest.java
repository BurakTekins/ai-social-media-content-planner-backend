package com.globalcodelabs.socialmediaplanner.integration;

import com.globalcodelabs.socialmediaplanner.domain.model.ApplicationSetting;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApplicationSettingRepository;
import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettingsService;
import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettings;
import com.globalcodelabs.socialmediaplanner.infrastructure.budget.GenerationBudgetProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import java.math.BigDecimal;
import java.time.Duration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.config.location=optional:classpath:/test-isolated.properties",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = SettingsPersistenceTest.Config.class)
@Testcontainers
class SettingsPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ApplicationSetting.class)
    @EnableJpaRepositories(basePackageClasses = ApplicationSettingRepository.class)
    @Import(GeneralSettingsService.class)
    static class Config {
        @Bean PublishingProperties publishing() { return new PublishingProperties(); }
        @Bean GenerationBudgetProperties budget() { return new GenerationBudgetProperties(); }
    }

    @Autowired ApplicationSettingRepository repository;
    @Autowired GeneralSettingsService service;
    @Autowired EntityManager entityManager;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    private GeneralSettings settings() {
        return new GeneralSettings(Duration.ofSeconds(120), Duration.ofSeconds(30), 10, 20,
                0, 0, new BigDecimal("12.34"), 2000, 300);
    }

    @Test
    void repeatedUpdatesPersistOneRowPerSetting() {
        service.update(settings());
        service.update(settings());
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.count()).isEqualTo(9);
        assertThat(service.get()).isEqualTo(settings());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void storageFailureRollsBackAllSettingsWrittenByService() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE FUNCTION reject_test_setting() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
                IF NEW.setting_key = 'generation.max-images-per-batch' THEN
                    RAISE EXCEPTION 'injected storage failure';
                END IF;
                RETURN NEW;
            END $$;
            CREATE TRIGGER reject_test_setting BEFORE INSERT ON application_setting
            FOR EACH ROW EXECUTE FUNCTION reject_test_setting();
            """);
        try {
            assertThatThrownBy(() -> service.update(settings()))
                    .isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining("injected storage failure");
            assertThat(repository.count()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_setting ON application_setting");
            jdbc.execute("DROP FUNCTION reject_test_setting()");
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void settingsJoinCallerTransactionAndRollbackTogether() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.update(settings());
            entityManager.flush();
            throw new IllegalStateException("abort operation");
        })).isInstanceOf(IllegalStateException.class).hasMessage("abort operation");
        assertThat(repository.count()).isZero();
    }
}
