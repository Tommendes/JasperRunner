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

    public void sendWelcomeEmail(String to, String name, String setupLink) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("MAILER_USER não configurado — e-mail de boas-vindas não enviado para {}", to);
            return;
        }

        String greeting = name != null && !name.isBlank() ? name : "novo usuário";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("JasperRunner — Bem-vindo(a)");
        message.setText("""
            Olá, %s!

            Sua conta no JasperRunner foi criada.

            Acesse o link abaixo para definir sua senha de acesso (válido por tempo limitado):

            %s

            Após definir a senha, utilize seu nome de usuário para entrar no sistema.

            Se você não esperava este e-mail, ignore esta mensagem.
            """.formatted(greeting, setupLink));

        mailSender.send(message);
        log.info("E-mail de boas-vindas enviado para {}", to);
    }
}
