package com.college.collegeevent;

import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventName;

    @Column(length = 1500)
    private String description;

    private LocalDate eventDate;

    private String venue;

    private String organizerEmail;

    private boolean published = true;

    // Maximum seats. null = no limit
    private Integer maxSeats;

    // Last day students can register. null = the event date
    private LocalDate registrationDeadline;

    // Not stored in the database - filled in before a page is shown
    @Transient
    private long registeredCount;

    // ---------- Getters and Setters ----------

    public Long getId() { return id; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getOrganizerEmail() { return organizerEmail; }
    public void setOrganizerEmail(String organizerEmail) { this.organizerEmail = organizerEmail; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public Integer getMaxSeats() { return maxSeats; }
    public void setMaxSeats(Integer maxSeats) { this.maxSeats = maxSeats; }

    public LocalDate getRegistrationDeadline() { return registrationDeadline; }
    public void setRegistrationDeadline(LocalDate registrationDeadline) {
        this.registrationDeadline = registrationDeadline;
    }

    public long getRegisteredCount() { return registeredCount; }
    public void setRegisteredCount(long registeredCount) { this.registeredCount = registeredCount; }

    // ---------- Helpers used by the pages ----------

    // true when a seat limit exists and all seats are taken
    public boolean isFull() {
        return maxSeats != null && registeredCount >= maxSeats;
    }

    // seats still available (null when there is no limit)
    public Long getSeatsLeft() {
        if (maxSeats == null) {
            return null;
        }
        return Math.max(0L, maxSeats - registeredCount);
    }

    // true when the event date has passed
    public boolean isFinished() {
        return eventDate != null && eventDate.isBefore(LocalDate.now());
    }

    // The real last day to register (never later than the event date)
    public LocalDate getEffectiveDeadline() {
        if (registrationDeadline == null) {
            return eventDate;
        }
        if (eventDate != null && registrationDeadline.isAfter(eventDate)) {
            return eventDate;
        }
        return registrationDeadline;
    }

    // true once the last registration day has passed
    public boolean isRegistrationClosed() {
        LocalDate deadline = getEffectiveDeadline();
        return deadline != null && deadline.isBefore(LocalDate.now());
    }
}