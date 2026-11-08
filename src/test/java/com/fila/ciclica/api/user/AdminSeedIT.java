package com.fila.ciclica.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fila.ciclica.domain.Role;
import com.fila.ciclica.domain.User;
import com.fila.ciclica.repository.UserRepository;
import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(ProofReporter.class)
class AdminSeedIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminSeedRunner adminSeedRunner;

    @Test
    @DisplayName("C6 startup seeds admin")
    void startupSeedsAdmin() {
        User admin = userRepository.findByEmail("admin@test.local").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("admin12345", admin.getPasswordHash())).isTrue();

        String originalHash = admin.getPasswordHash();
        adminSeedRunner.run(null);

        User unchanged = userRepository.findByEmail("admin@test.local").orElseThrow();
        assertThat(unchanged.getPasswordHash()).isEqualTo(originalHash);
        assertThat(unchanged.isActive()).isTrue();
    }
}
