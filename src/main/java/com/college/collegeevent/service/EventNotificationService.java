package com.college.collegeevent.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.collegeevent.AppUser;
import com.college.collegeevent.Event;
import com.college.collegeevent.Notification;
import com.college.collegeevent.repository.AppUserRepository;
import com.college.collegeevent.repository.NotificationRepository;

@Service
public class EventNotificationService {

    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailNotifier emailNotifier;

    @Value("${app.notifications.email-enabled:false}")
    private boolean emailEnabled;

    public EventNotificationService(
            AppUserRepository userRepository,
            NotificationRepository notificationRepository,
            EmailNotifier emailNotifier) {

        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.emailNotifier = emailNotifier;
    }

    @Transactional
    public int notifyStudentsAboutNewEvent(Event event) {

        List<AppUser> students = userRepository.findByRole("STUDENT");

        if (students.isEmpty()) {
            return 0;
        }

        String text = "New event: " + event.getEventName()
                + " on " + event.getEventDate()
                + " at " + event.getVenue();

        // Tell students when registration closes before the event itself
        LocalDate deadline = event.getEffectiveDeadline();

        if (deadline != null && !deadline.equals(event.getEventDate())) {
            text += ". Register by " + deadline;
        }

        if (text.length() > 500) {
            text = text.substring(0, 497) + "...";
        }

        final String message = text;

        List<Notification> notifications = students.stream()
                .map(student -> {
                    Notification n = new Notification();
                    n.setUserEmail(student.getEmail());
                    n.setEventId(event.getId());
                    n.setMessage(message);
                    return n;
                })
                .toList();

        notificationRepository.saveAll(notifications);

        if (emailEnabled) {
            emailNotifier.sendNewEventEmails(
                    students.stream().map(AppUser::getEmail).toList(),
                    event.getEventName(),
                    String.valueOf(event.getEventDate()),
                    event.getVenue(),
                    event.getDescription() == null ? "" : event.getDescription());
        }

        return students.size();
    }
}