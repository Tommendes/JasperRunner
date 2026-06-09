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
    private final ApplicationUrlService applicationUrlService;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                UserService userService,
                                JasperRunnerProperties properties,
                                ApplicationUrlService applicationUrlService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.userService = userService;
        this.properties = properties;
        this.applicationUrlService = applicationUrlService;
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

        String resetLink = buildResetLink(createToken(user));
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    /** Envia e-mail de boas-vindas com link para o novo usuário definir a senha. */
    @Transactional
    public void sendWelcomePasswordSetup(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Usuário sem e-mail cadastrado");
        }

        String setupLink = buildResetLink(createToken(user));
        emailService.sendWelcomeEmail(user.getEmail(), user.getName(), setupLink);
    }

    private PasswordResetToken createToken(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(AppTimeZone.nowUtc().plusMinutes(properties.getPasswordResetExpirationMinutes()));
        return tokenRepository.save(token);
    }

    private String buildResetLink(PasswordResetToken token) {
        return applicationUrlService.resolveBaseUrl()
            + "/password/reset?token=" + token.getToken();
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
