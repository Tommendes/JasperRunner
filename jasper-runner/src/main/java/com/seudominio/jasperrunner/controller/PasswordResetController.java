package com.seudominio.jasperrunner.controller;

import com.seudominio.jasperrunner.dto.ForgotPasswordForm;
import com.seudominio.jasperrunner.dto.ResetPasswordForm;
import com.seudominio.jasperrunner.model.PasswordResetToken;
import com.seudominio.jasperrunner.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    private static final String GENERIC_RESET_MESSAGE =
        "Se o usuário existir e tiver e-mail cadastrado, você receberá um link de recuperação em breve.";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/password/forgot")
    public String forgotForm(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "password-forgot";
    }

    @PostMapping("/password/forgot")
    public String forgotSubmit(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return "password-forgot";
        }

        passwordResetService.requestReset(form.getUsername());
        ra.addFlashAttribute("success", GENERIC_RESET_MESSAGE);
        return "redirect:/login";
    }

    @GetMapping("/password/reset")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("error", "Link de recuperação inválido.");
            return "password-reset";
        }

        var tokenOpt = passwordResetService.findValidToken(token);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Link de recuperação inválido ou expirado.");
            return "password-reset";
        }

        ResetPasswordForm form = new ResetPasswordForm();
        form.setToken(token);
        model.addAttribute("resetPasswordForm", form);
        return "password-reset";
    }

    @PostMapping("/password/reset")
    public String resetSubmit(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return "password-reset";
        }

        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            model.addAttribute("error", "Nova senha e confirmação não conferem");
            return "password-reset";
        }

        PasswordResetToken token = passwordResetService.findValidToken(form.getToken())
            .orElse(null);

        if (token == null) {
            model.addAttribute("error", "Link de recuperação inválido ou expirado.");
            return "password-reset";
        }

        try {
            passwordResetService.consumeToken(token, form.getNewPassword());
            ra.addFlashAttribute("success", "Senha redefinida com sucesso. Faça login com a nova senha.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "password-reset";
        }
    }
}
