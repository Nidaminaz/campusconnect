package com.college.collegeevent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.collegeevent.PasswordResetToken;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByEmail(String email);
}