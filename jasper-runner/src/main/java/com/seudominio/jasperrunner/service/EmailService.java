package com.seudominio.jasperrunner.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("MAILER_USER não configurado — e-mail de recuperação não enviado para {}", to);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("JasperRunner — Recuperação de senha");
        message.setText("""
            Você solicitou a redefinição de senha no JasperRunner.

            Acesse o link abaixo para definir uma nova senha (válido por tempo limitado):

            %s

            Se você não solicitou esta alteração, ignore este e-mail.
            """.formatted(resetLink));

        mailSender.send(message);
        log.info("E-mail de recuperação enviado para {}", to);
    }
}
