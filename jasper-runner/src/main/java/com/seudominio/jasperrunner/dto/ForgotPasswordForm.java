package com.seudominio.jasperrunner.dto;

import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordForm {

    @NotBlank(message = "Usuário é obrigatório")
    private String username;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
