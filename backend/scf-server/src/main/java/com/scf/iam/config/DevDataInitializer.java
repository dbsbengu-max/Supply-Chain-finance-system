package com.scf.iam.config;

import com.scf.iam.entity.SysUser;
import com.scf.iam.repository.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = "scf.dev.password-bootstrap", havingValue = "true", matchIfMissing = true)
public class DevDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    @Bean
    CommandLineRunner initDevPasswords(SysUserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            Map<String, String> passwords = Map.of(
                    "platform_admin", "Admin@123",
                    "funding_user", "Fund@123",
                    "member_user", "Member@123",
                    "warehouse_user", "Wh@123"
            );
            for (Map.Entry<String, String> entry : passwords.entrySet()) {
                userRepository.findByLoginNameAndStatus(entry.getKey(), "ACTIVE").ifPresent(user -> {
                    if ("mock_hash".equals(user.getPasswordHash()) || !passwordEncoder.matches(entry.getValue(), user.getPasswordHash())) {
                        user.setPasswordHash(passwordEncoder.encode(entry.getValue()));
                        userRepository.save(user);
                        log.info("Updated dev password for user: {}", entry.getKey());
                    }
                });
            }
        };
    }
}
