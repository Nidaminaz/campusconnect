package com.college.collegeevent.service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.collegeevent.Event;
import com.college.collegeevent.Notification;
import com.college.collegeevent.Registration;
import com.college.collegeevent.repository.EventRepository;
import com.college.collegeevent.repository.NotificationRepository;
import com.college.collegeevent.repository.RegistrationRepository;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final NotificationRepository notificationRepository;

    public EventService(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            NotificationRepository notificationRepository) {

        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.notificationRepository = notificationRepository;
    }

    // Fills in how many students registered for each event
    public void attachRegistrationCounts(List<Event> events) {
        for (Event event : events) {
            event.setRegisteredCount(
                registrationRepository.countByEventId(event.getId()));
        }
    }

    // Deletes an event, removes its registrations and tells registered students.
    // Returns the number of students who were notified.
    @Transactional
    public int deleteEvent(Event event) {

        List<Registration> registrations =
            registrationRepository.findByEventId(event.getId());

        // Old notifications for this event are no longer useful
        notificationRepository.deleteByEventId(event.getId());

        int notified = notifyStudents(
            registrations,
            "Event cancelled: " + event.getEventName()
                + " (" + event.getEventDate() + ")",
            null);

        registrationRepository.deleteAll(registrations);
        eventRepository.deleteById(event.getId());

        return notified;
    }

    // Updates an event. Registered students are notified if the name, date,
    // venue or registration deadline changed. Returns true when students
    // were notified.
    @Transactional
    public boolean updateEvent(Event event,
                               String eventName,
                               String description,
                               LocalDate eventDate,
                               String venue,
                               Integer maxSeats,
                               LocalDate registrationDeadline) {

        boolean nameChanged = !Objects.equals(event.getEventName(), eventName);
        boolean changed = nameChanged
                || !Objects.equals(event.getEventDate(), eventDate)
                || !Objects.equals(event.getVenue(), venue)
                || !Objects.equals(event.getRegistrationDeadline(), registrationDeadline);

        event.setEventName(eventName);
        event.setDescription(description);
        event.setEventDate(eventDate);
        event.setVenue(venue);
        event.setMaxSeats(maxSeats);
        event.setRegistrationDeadline(registrationDeadline);

        eventRepository.save(event);

        List<Registration> registrations =
            registrationRepository.findByEventId(event.getId());

        // Registrations keep a copy of the event name, so keep it in sync
        if (nameChanged) {
            registrations.forEach(r -> r.setEventName(eventName));
            registrationRepository.saveAll(registrations);
        }

        if (changed) {
            notifyStudents(
                registrations,
                "Event updated: " + eventName + " is on " + eventDate
                    + " at " + venue + ". Registration closes "
                    + event.getEffectiveDeadline() + ".",
                event.getId());
        }

        return changed && !registrations.isEmpty();
    }

    // Creates one notification per distinct student email
    private int notifyStudents(List<Registration> registrations,
                               String text,
                               Long eventId) {

        Set<String> emails = registrations.stream()
            .map(r -> r.getEmail().toLowerCase())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (emails.isEmpty()) {
            return 0;
        }

        final String message = text.length() > 500
            ? text.substring(0, 497) + "..."
            : text;

        List<Notification> notifications = emails.stream()
            .map(email -> {
                Notification n = new Notification();
                n.setUserEmail(email);
                n.setEventId(eventId);
                n.setMessage(message);
                return n;
            })
            .toList();

        notificationRepository.saveAll(notifications);

        return emails.size();
    }
}