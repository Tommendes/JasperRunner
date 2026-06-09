package com.seudominio.jasperrunner.controller;

import com.seudominio.jasperrunner.dto.ChangePasswordForm;
import com.seudominio.jasperrunner.dto.ProfileForm;
import com.seudominio.jasperrunner.model.User;
import com.seudominio.jasperrunner.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userService.findByUsername(principal.getUsername());

        ProfileForm profileForm = new ProfileForm();
        profileForm.setName(user.getName());
        profileForm.setEmail(user.getEmail());

        model.addAttribute("profileForm", profileForm);
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal UserDetails principal,
                                @Valid @ModelAttribute("profileForm") ProfileForm form,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            populateProfileModel(model, principal.getUsername());
            model.addAttribute("changePasswordForm", new ChangePasswordForm());
            return "profile";
        }

        try {
            userService.updateProfile(principal.getUsername(), form);
            ra.addFlashAttribute("success", "Perfil atualizado com sucesso.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@AuthenticationPrincipal UserDetails principal,
                                 @Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            populateProfileModel(model, principal.getUsername());
            model.addAttribute("profileForm", profileFormFromUser(principal.getUsername()));
            return "profile";
        }

        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            populateProfileModel(model, principal.getUsername());
            model.addAttribute("profileForm", profileFormFromUser(principal.getUsername()));
            model.addAttribute("error", "Nova senha e confirmação não conferem");
            return "profile";
        }

        try {
            userService.changePassword(principal.getUsername(), form);
            ra.addFlashAttribute("success", "Senha alterada com sucesso.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/profile";
    }

    private void populateProfileModel(Model model, String username) {
        model.addAttribute("user", userService.findByUsername(username));
    }

    private ProfileForm profileFormFromUser(String username) {
        User user = userService.findByUsername(username);
        ProfileForm form = new ProfileForm();
        form.setName(user.getName());
        form.setEmail(user.getEmail());
        return form;
    }
}
