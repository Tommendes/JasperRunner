package com.seudominio.jasperrunner.service;

import com.seudominio.jasperrunner.config.JasperRunnerProperties;
import com.seudominio.jasperrunner.model.PasswordResetToken;
import com.seudominio.jasperrunner.model.User;
import com.seudominio.jasperrunner.repository.PasswordResetTokenRepository;
import com.seudominio.jasperrunner.repository.UserRepository;
import com.seudominio.jasperrunner.util.AppTimeZone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final JasperRunnerProperties properties;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                UserService userService,
                                JasperRunnerProperties properties) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.userService = userService;
        this.properties = properties;
    }

    /**
     * Solicita reset de senha. Sempre retorna sem erro para não revelar existência de usuário.
     */
    @Transactional
    public void requestReset(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            return;
        }

        User user = userOpt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(AppTimeZone.nowUtc().plusMinutes(properties.getPasswordResetExpirationMinutes()));
        tokenRepository.save(token);

        String resetLink = properties.getBaseUrl().replaceAll("/$", "")
            + "/password/reset?token=" + token.getToken();

        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> findValidToken(String tokenValue) {
        return tokenRepository.findByToken(tokenValue)
            .filter(PasswordResetToken::isValid);
    }

    @Transactional
    public void consumeToken(PasswordResetToken token, String newPassword) {
        if (!token.isValid()) {
            throw new IllegalArgumentException("Link de recuperação inválido ou expirado");
        }

        userService.resetPassword(token.getUserId(), newPassword);

        token.setUsedAt(AppTimeZone.nowUtc());
        tokenRepository.save(token);
    }
}
