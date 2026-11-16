package com.college.collegeevent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.collegeevent.AppUser;
import com.college.collegeevent.Event;
import com.college.collegeevent.Notification;
import com.college.collegeevent.Registration;
import com.college.collegeevent.repository.AppUserRepository;
import com.college.collegeevent.repository.EventRepository;
import com.college.collegeevent.repository.NotificationRepository;
import com.college.collegeevent.repository.RegistrationRepository;

/**
 * Creates reminders that appear as popups on the student dashboard.
 *
 *  - Students who are NOT registered: reminded about the registration
 *    deadline (tomorrow / today).
 *  - Students who ARE registered: reminded about the event itself
 *    (tomorrow / today).
 */
@Service
public class ReminderService {

    private static final String EVENT_TOMORROW = "EVENT_TOMORROW";
    private static final String EVENT_TODAY = "EVENT_TODAY";
    private static final String DEADLINE_TOMORROW = "DEADLINE_TOMORROW";
    private static final String DEADLINE_TODAY = "DEADLINE_TODAY";

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public ReminderService(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            AppUserRepository userRepository,
            NotificationRepository notificationRepository) {

        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    // Runs 10 seconds after start-up, then 30 minutes after each run finishes
    @Scheduled(initialDelay = 10_000, fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void sendReminders() {

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<Event> upcoming = eventRepository
            .findByPublishedTrueAndEventDateGreaterThanEqualOrderByEventDateAsc(today);

        if (upcoming.isEmpty()) {
            return;
        }

        List<AppUser> students = userRepository.findByRole("STUDENT");

        if (students.isEmpty()) {
            return;
        }

        List<Notification> toSave = new ArrayList<>();

        for (Event event : upcoming) {

            LocalDate eventDay = event.getEventDate();
            LocalDate deadline = event.getEffectiveDeadline();

            boolean eventToday = eventDay.equals(today);
            boolean eventTomorrow = eventDay.equals(tomorrow);
            boolean deadlineToday = deadline.equals(today);
            boolean deadlineTomorrow = deadline.equals(tomorrow);

            // Nothing to remind about for this event right now
            if (!eventToday && !eventTomorrow
                    && !deadlineToday && !deadlineTomorrow) {
                continue;
            }

            List<Registration> registrations =
                registrationRepository.findByEventId(event.getId());

            Set<String> registeredEmails = registrations.stream()
                .map(r -> r.getEmail().toLowerCase())
                .collect(Collectors.toSet());

            boolean full = event.getMaxSeats() != null
                    && registrations.size() >= event.getMaxSeats();

            for (AppUser student : students) {

                String email = student.getEmail();
                boolean registered = registeredEmails.contains(email.toLowerCase());

                if (registered) {

                    if (eventToday) {
                        addIfNew(toSave, email, event, EVENT_TODAY,
                            "Today: " + event.getEventName()
                                + " is happening at " + event.getVenue()
                                + ". See you there!");

                    } else if (eventTomorrow) {
                        addIfNew(toSave, email, event, EVENT_TOMORROW,
                            "Reminder: " + event.getEventName()
                                + " is tomorrow at " + event.getVenue() + ".");
                    }

                } else {

                    // A full event has no "register now" reminder
                    if (full) {
                        continue;
                    }

                    if (deadlineToday) {
                        addIfNew(toSave, email, event, DEADLINE_TODAY,
                            "Last day to register! " + event.getEventName()
                                + " closes registration today.");

                    } else if (deadlineTomorrow) {
                        addIfNew(toSave, email, event, DEADLINE_TOMORROW,
                            "Hurry! Registration for " + event.getEventName()
                                + " closes tomorrow (" + deadline
                                + "). Register now.");
                    }
                }
            }
        }

        notificationRepository.saveAll(toSave);
    }

    // Adds the reminder only if this student has not received it before
    private void addIfNew(List<Notification> list,
                          String email,
                          Event event,
                          String type,
                          String text) {

        if (notificationRepository
                .existsByUserEmailIgnoreCaseAndEventIdAndType(
                    email, event.getId(), type)) {
            return;
        }

        String message = text.length() > 500
            ? text.substring(0, 497) + "..."
            : text;

        Notification n = new Notification();
        n.setUserEmail(email);
        n.setEventId(event.getId());
        n.setMessage(message);
        n.setType(type);

        list.add(n);
    }
}