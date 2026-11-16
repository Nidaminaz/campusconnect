package com.college.collegeevent.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.collegeevent.AppUser;
import com.college.collegeevent.PasswordResetToken;
import com.college.collegeevent.repository.AppUserRepository;
import com.college.collegeevent.repository.PasswordResetTokenRepository;

@Service
public class PasswordResetService {

    private final AppUserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.password-reset.base-url}")
    private String baseUrl;

    @Value("${app.password-reset.expiry-minutes:15}")
    private long expiryMinutes;

    public PasswordResetService(
            AppUserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            JavaMailSender mailSender) {

        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public void sendResetLink(String email) {

        String normalizedEmail = email.trim().toLowerCase();

        // Do not reveal whether an account exists.
        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElse(null);

        if (user == null) {
            return;
        }

        // Remove previous reset tokens for this email.
        tokenRepository.deleteByEmail(normalizedEmail);

        String token = UUID.randomUUID().toString();

        LocalDateTime expiryDate =
                LocalDateTime.now().plusMinutes(expiryMinutes);

        PasswordResetToken resetToken =
                new PasswordResetToken(
                        token,
                        normalizedEmail,
                        expiryDate
                );

        tokenRepository.save(resetToken);

        String resetLink =
                baseUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(normalizedEmail);
        message.setSubject("CampusConnect - Password Reset");

        message.setText(
                "Hello " + user.getFullName() + ",\n\n"
                + "We received a request to reset your CampusConnect password.\n\n"
                + "Click the link below to create a new password:\n"
                + resetLink + "\n\n"
                + "This link expires in " + expiryMinutes + " minutes.\n\n"
                + "If you did not request this, please ignore this email."
        );

        mailSender.send(message);
    }
}