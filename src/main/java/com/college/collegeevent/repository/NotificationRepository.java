package com.college.collegeevent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.college.collegeevent.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop20ByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    List<Notification> findTop20ByUserEmailIgnoreCaseAndSeenFalseOrderByCreatedAtDesc(String email);

    long countByUserEmailIgnoreCaseAndSeenFalse(String email);

    // Has this student already received this kind of reminder for this event?
    boolean existsByUserEmailIgnoreCaseAndEventIdAndType(String email, Long eventId, String type);

    // Used when an event is deleted (called inside a transactional service)
    void deleteByEventId(Long eventId);

    @Modifying
    @Transactional
    @Query("update Notification n set n.seen = true "
         + "where lower(n.userEmail) = lower(:email) and n.seen = false")
    int markAllSeen(@Param("email") String email);
}