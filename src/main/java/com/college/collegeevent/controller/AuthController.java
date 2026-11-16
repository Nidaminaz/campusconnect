package com.college.collegeevent.controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.college.collegeevent.AppUser;
import com.college.collegeevent.repository.AppUserRepository;

@Controller
public class AuthController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Old /login links go to the student login page
    @GetMapping("/login")
    public String login() {
        return "redirect:/student/login";
    }

    @GetMapping("/student/login")
    public String studentLogin() {
        return "student-login";
    }

    @GetMapping("/organizer/login")
    public String organizerLogin() {
        return "organizer-login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @PostMapping("/signup")
    public String createStudent(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            Model model) {

        if (fullName == null || fullName.trim().isEmpty()
                || email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()) {

            model.addAttribute("error", "Please fill in all required fields.");
            return "signup";
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            model.addAttribute("error", "This email is already registered.");
            return "signup";
        }

        if (password.length() < 8) {
            model.addAttribute("error",
                    "Password must contain at least 8 characters.");
            return "signup";
        }

        AppUser student = new AppUser();

        student.setFullName(fullName.trim());
        student.setEmail(normalizedEmail);
        student.setPassword(passwordEncoder.encode(password));
        student.setRole("STUDENT");

        userRepository.save(student);

        return "redirect:/student/login?registered=true";
    }
}