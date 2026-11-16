package com.college.collegeevent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();

        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    // Redirect by role. A student cannot log in through the organizer page
    // (and an organizer cannot log in through the student page).
    @Bean
    public AuthenticationSuccessHandler loginSuccessHandler() {

        return (request, response, authentication) -> {

            boolean organizer = authentication.getAuthorities()
                    .stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));

            String loginType = request.getParameter("loginType");

            if (loginType != null
                    && organizer != "organizer".equals(loginType)) {

                new SecurityContextLogoutHandler()
                        .logout(request, response, authentication);

                response.sendRedirect(
                        "organizer".equals(loginType)
                                ? "/organizer/login?error=true"
                                : "/student/login?error=true");
                return;
            }

            if (organizer) {
                response.sendRedirect("/organizer/dashboard");
            } else {
                response.sendRedirect("/student/dashboard");
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler loginFailureHandler() {

        return (request, response, exception) -> {

            String loginType = request.getParameter("loginType");

            if ("organizer".equals(loginType)) {
                response.sendRedirect("/organizer/login?error=true");
            } else {
                response.sendRedirect("/student/login?error=true");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler loginSuccessHandler,
            AuthenticationFailureHandler loginFailureHandler,
            DaoAuthenticationProvider authenticationProvider)
            throws Exception {

        http
            .authenticationProvider(authenticationProvider)

            .authorizeHttpRequests(auth -> auth

                // Public pages
                .requestMatchers(
                    "/",
                    "/login",
                    "/student/login",
                    "/organizer/login",
                    "/signup",
                    "/forgot-password",
                    "/reset-password",
                    "/error",
                    "/favicon.ico"
                ).permitAll()

                // Organizer pages
                .requestMatchers("/organizer/**").hasRole("ORGANIZER")

                // Student pages (including event registration)
                .requestMatchers("/student/**").hasRole("STUDENT")

                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/student/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )

            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }
}