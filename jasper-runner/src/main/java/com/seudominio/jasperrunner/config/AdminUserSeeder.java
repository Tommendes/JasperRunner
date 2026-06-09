package com.seudominio.jasperrunner.config;

import com.seudominio.jasperrunner.model.User;
import com.seudominio.jasperrunner.model.UserRole;
import com.seudominio.jasperrunner.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JasperRunnerProperties properties;

    public AdminUserSeeder(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JasperRunnerProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.getAdminUser();
        try {
            if (userRepository.existsByUsername(username)) {
                log.info("Usuário admin '{}' já existe — seed ignorado.", username);
                return;
            }

            User admin = new User();
            admin.setUsername(username);
            admin.setName(properties.getAdminName());
            admin.setEmail(properties.getAdminEmail());
            admin.setRole(UserRole.ADMIN);
            admin.setPasswordHash(passwordEncoder.encode(properties.getAdminPassword()));
            admin.setEnabled(true);

            userRepository.saveAndFlush(admin);
            log.info("Usuário admin inicial criado: {} (tabela app_users)", username);
        } catch (Exception e) {
            log.error("Falha ao criar usuário admin '{}': {}", username, e.getMessage(), e);
            throw e;
        }
    }
}
