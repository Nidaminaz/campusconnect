
package com.college.collegeevent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.college.collegeevent.repository.AppUserRepository;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner createOrganizer(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${app.organizer.email}") String email,
            @Value("${app.organizer.password}") String password) {

        return args -> {

            String organizerEmail = email.trim().toLowerCase();

            // Check whether organizer already exists
            if (!repository.existsByEmail(organizerEmail)) {

                AppUser organizer = new AppUser();

                organizer.setFullName("Campus Event Organizer");
                organizer.setEmail(organizerEmail);

                // Encrypt password before saving
                organizer.setPassword(
                    passwordEncoder.encode(password)
                );

                organizer.setRole("ORGANIZER");

                repository.save(organizer);

                System.out.println("Organizer account created successfully!");
            } else {
                System.out.println("Organizer account already exists.");
            }
        };
    }
}
