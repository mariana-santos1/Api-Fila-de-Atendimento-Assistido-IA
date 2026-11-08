package com.fila.ciclica.api.user;

import com.fila.ciclica.config.AppProperties;
import com.fila.ciclica.domain.Role;
import com.fila.ciclica.domain.User;
import com.fila.ciclica.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminSeedRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public AdminSeedRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = appProperties.admin().email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            return;
        }

        Instant now = Instant.now();
        User admin = new User();
        admin.setName("Administrator");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(appProperties.admin().password()));
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userRepository.save(admin);
    }
}
