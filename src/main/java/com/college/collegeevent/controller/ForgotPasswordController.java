
package com.college.collegeevent.controller;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.college.collegeevent.AppUser;
import com.college.collegeevent.PasswordResetToken;
import com.college.collegeevent.repository.AppUserRepository;
import com.college.collegeevent.repository.PasswordResetTokenRepository;

@Controller
public class ForgotPasswordController {

    private final PasswordResetService resetService;
    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ForgotPasswordController(
            PasswordResetService resetService,
            PasswordResetTokenRepository tokenRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.resetService = resetService;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Display Forgot Password Page
    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    // Send Password Reset Link
    @PostMapping("/forgot-password")
    public String sendResetLink(
            @RequestParam String email,
            Model model) {

        if (email == null || email.trim().isEmpty()) {
            model.addAttribute(
                    "error",
                    "Please enter your email address."
            );
            return "forgot-password";
        }

        try {
            resetService.sendResetLink(email.trim().toLowerCase());

            model.addAttribute(
                    "message",
                    "If an account exists with this email, a password reset link has been sent."
            );

        } catch (Exception e) {

            System.err.println("========== PASSWORD RESET EMAIL ERROR ==========");
            e.printStackTrace();
            System.err.println("=================================================");

            model.addAttribute(
                    "error",
                    "Unable to send the email right now. Please check the application configuration."
            );
        }

        return "forgot-password";
    }

    // Display Reset Password Page
    @GetMapping("/reset-password")
    public String resetPasswordPage(
            @RequestParam String token,
            Model model) {

        PasswordResetToken resetToken =
                tokenRepository.findByToken(token).orElse(null);

        if (!isValid(resetToken)) {
            model.addAttribute(
                    "error",
                    "This password reset link is invalid or expired."
            );
            return "reset-password";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    // Update Password
    @PostMapping("/reset-password")
    public String updatePassword(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {

        PasswordResetToken resetToken =
                tokenRepository.findByToken(token).orElse(null);

        if (!isValid(resetToken)) {
            model.addAttribute(
                    "error",
                    "This password reset link is invalid or expired."
            );
            return "reset-password";
        }

        if (password == null || password.length() < 8) {
            model.addAttribute(
                    "error",
                    "Password must contain at least 8 characters."
            );
            model.addAttribute("token", token);
            return "reset-password";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute(
                    "error",
                    "Passwords do not match."
            );
            model.addAttribute("token", token);
            return "reset-password";
        }

        AppUser user = userRepository
                .findByEmail(resetToken.getEmail())
                .orElse(null);

        if (user == null) {
            model.addAttribute(
                    "error",
                    "User account not found."
            );
            return "reset-password";
        }

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        // Mark reset token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        model.addAttribute(
                "message",
                "Your password has been updated successfully. Please log in."
        );

        return "reset-password";
    }

    // Validate Reset Token
    private boolean isValid(PasswordResetToken resetToken) {

        return resetToken != null
                && !resetToken.isUsed()
                && resetToken.getExpiryDate() != null
                && resetToken.getExpiryDate().isAfter(LocalDateTime.now());
    }
}
