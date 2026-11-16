package com.college.collegeevent.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.collegeevent.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

    // All published events
    List<Event> findByPublishedTrueOrderByEventDateAsc();

    // Published events happening today or later (finished events are hidden)
    List<Event> findByPublishedTrueAndEventDateGreaterThanEqualOrderByEventDateAsc(LocalDate date);

    // Published events on one exact date (used for reminders)
    List<Event> findByPublishedTrueAndEventDate(LocalDate date);

    // Events created by a particular organizer
    List<Event> findByOrganizerEmailOrderByEventDateAsc(String organizerEmail);
}