package com.seudominio.jasperrunner.controller;

import com.seudominio.jasperrunner.dto.UserCreateForm;
import com.seudominio.jasperrunner.model.User;
import com.seudominio.jasperrunner.model.UserRole;
import com.seudominio.jasperrunner.service.PasswordResetService;
import com.seudominio.jasperrunner.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public UserController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("userCreateForm", new UserCreateForm());
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("editing", false);
        model.addAttribute("pageTitle", "Novo Usuário");
        return "user-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            User user = userService.findById(id);
            UserCreateForm form = new UserCreateForm();
            form.setName(user.getName());
            form.setEmail(user.getEmail());
            form.setUsername(user.getUsername());
            form.setRole(user.getRole());
            form.setEnabled(user.isEnabled());

            model.addAttribute("userCreateForm", form);
            model.addAttribute("roles", UserRole.values());
            model.addAttribute("editing", true);
            model.addAttribute("userId", id);
            model.addAttribute("pageTitle", "Editar Usuário");
            return "user-form";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/users";
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userCreateForm") UserCreateForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, true, id, "Editar Usuário");
            return "user-form";
        }

        try {
            User user = userService.updateByAdmin(id, form, principal.getUsername());
            ra.addFlashAttribute("success", "Usuário '" + user.getUsername() + "' atualizado com sucesso.");
            return "redirect:/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            populateFormModel(model, true, id, "Editar Usuário");
            return "user-form";
        } catch (Exception e) {
            log.error("Erro ao atualizar usuário {}", id, e);
            model.addAttribute("error", "Erro ao atualizar usuário: " + e.getMessage());
            populateFormModel(model, true, id, "Editar Usuário");
            return "user-form";
        }
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("userCreateForm") UserCreateForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, false, null, "Novo Usuário");
            return "user-form";
        }

        try {
            User user = userService.createByAdmin(form);
            passwordResetService.sendWelcomePasswordSetup(user);
            ra.addFlashAttribute("success",
                "Usuário '" + user.getUsername() + "' criado. E-mail de boas-vindas enviado para " + user.getEmail() + ".");
            return "redirect:/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            populateFormModel(model, false, null, "Novo Usuário");
            return "user-form";
        } catch (Exception e) {
            log.error("Erro ao criar usuário {}", form.getUsername(), e);
            model.addAttribute("error", "Erro ao criar usuário: " + e.getMessage());
            populateFormModel(model, false, null, "Novo Usuário");
            return "user-form";
        }
    }

    private void populateFormModel(Model model, boolean editing, Long userId, String pageTitle) {
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("editing", editing);
        if (userId != null) {
            model.addAttribute("userId", userId);
        }
        model.addAttribute("pageTitle", pageTitle);
    }
}
