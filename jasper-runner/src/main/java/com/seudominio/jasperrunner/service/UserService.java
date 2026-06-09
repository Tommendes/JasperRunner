package com.seudominio.jasperrunner.service;

import com.seudominio.jasperrunner.dto.ChangePasswordForm;
import com.seudominio.jasperrunner.dto.ProfileForm;
import com.seudominio.jasperrunner.dto.UserCreateForm;
import com.seudominio.jasperrunner.model.User;
import com.seudominio.jasperrunner.model.UserRole;
import com.seudominio.jasperrunner.repository.UserRepository;
import com.seudominio.jasperrunner.util.PasswordPolicy;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
    }

    @Transactional
    public User createByAdmin(UserCreateForm form) {
        String username = form.getUsername().trim();
        String email = form.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Nome de usuário já está em uso");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("E-mail já está em uso");
        }

        User user = new User();
        user.setUsername(username);
        user.setName(form.getName().trim());
        user.setEmail(email);
        user.setRole(form.getRole());
        user.setEnabled(form.isEnabled());
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

        return userRepository.save(user);
    }

    @Transactional
    public User updateByAdmin(Long id, UserCreateForm form, String actorUsername) {
        User user = findById(id);
        String username = form.getUsername().trim();
        String email = form.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsernameAndIdNot(username, id)) {
            throw new IllegalArgumentException("Nome de usuário já está em uso");
        }
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new IllegalArgumentException("E-mail já está em uso");
        }

        if (user.getUsername().equals(actorUsername)) {
            if (!form.isEnabled()) {
                throw new IllegalArgumentException("Você não pode desativar sua própria conta");
            }
            if (form.getRole() != UserRole.ADMIN) {
                throw new IllegalArgumentException("Você não pode remover seu próprio perfil de administrador");
            }
        }

        user.setUsername(username);
        user.setName(form.getName().trim());
        user.setEmail(email);
        user.setRole(form.getRole());
        user.setEnabled(form.isEnabled());

        return userRepository.save(user);
    }

    @Transactional
    public void updateProfile(String username, ProfileForm form) {
        User user = findByUsername(username);

        if (userRepository.existsByEmailAndIdNot(form.getEmail(), user.getId())) {
            throw new IllegalArgumentException("Este e-mail já está em uso");
        }

        user.setName(form.getName().trim());
        user.setEmail(form.getEmail().trim().toLowerCase());
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordForm form) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Nova senha e confirmação não conferem");
        }
        if (!PasswordPolicy.isValid(form.getNewPassword())) {
            throw new IllegalArgumentException("Nova senha deve ter no mínimo 8 caracteres");
        }

        User user = findByUsername(username);
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }

        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new IllegalArgumentException("Nova senha deve ter no mínimo 8 caracteres");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
