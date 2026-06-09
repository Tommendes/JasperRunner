package com.seudominio.jasperrunner.dto;

import com.seudominio.jasperrunner.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserCreateForm {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Usuário é obrigatório")
    @Size(min = 3, max = 64, message = "Usuário deve ter entre 3 e 64 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Usuário pode conter apenas letras, números, ponto, hífen e sublinhado")
    private String username;

    @NotNull(message = "Perfil é obrigatório")
    private UserRole role;

    private boolean enabled = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
