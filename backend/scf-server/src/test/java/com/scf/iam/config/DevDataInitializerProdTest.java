package com.scf.iam.config;

import com.scf.iam.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DevDataInitializerProdTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevDataInitializer.class, TestBeans.class);

    @Test
    void beanAbsentUnderProdProfile() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("initDevPasswords"));
    }

    @Test
    void beanAbsentWhenBootstrapDisabled() {
        runner.withPropertyValues(
                        "spring.profiles.active=dev",
                        "scf.dev.password-bootstrap=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("initDevPasswords"));
    }

    @Test
    void beanPresentInDevWithBootstrapEnabled() {
        runner.withPropertyValues(
                        "spring.profiles.active=dev",
                        "scf.dev.password-bootstrap=true")
                .run(ctx -> assertThat(ctx).hasBean("initDevPasswords"));
    }

    @Configuration
    static class TestBeans {
        @Bean
        SysUserRepository sysUserRepository() {
            return mock(SysUserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
