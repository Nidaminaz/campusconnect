package com.college.collegeevent.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.collegeevent.Registration;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    List<Registration> findByEmailIgnoreCaseOrderByRegisteredAtDesc(String email);

    boolean existsByEmailIgnoreCaseAndEventId(String email, Long eventId);

    List<Registration> findByEventIdInOrderByRegisteredAtDesc(Collection<Long> eventIds);

    // All registrations of one event
    List<Registration> findByEventId(Long eventId);

    // How many seats are taken
    long countByEventId(Long eventId);
}