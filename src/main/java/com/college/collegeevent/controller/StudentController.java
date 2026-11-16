package com.college.collegeevent.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.college.collegeevent.AppUser;
import com.college.collegeevent.Event;
import com.college.collegeevent.Notification;
import com.college.collegeevent.Registration;
import com.college.collegeevent.repository.AppUserRepository;
import com.college.collegeevent.repository.EventRepository;
import com.college.collegeevent.repository.NotificationRepository;
import com.college.collegeevent.repository.RegistrationRepository;
import com.college.collegeevent.service.EventService;

@Controller
public class StudentController {

    // Makes sure two students cannot take the last seat at the same moment
    private static final Object REGISTRATION_LOCK = new Object();

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EventService eventService;

    public StudentController(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            AppUserRepository userRepository,
            NotificationRepository notificationRepository,
            EventService eventService) {

        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.eventService = eventService;
    }

    // Message shown when the registration deadline has passed
    private String closedMessage(Event event) {
        return "Registration for \"" + event.getEventName()
                + "\" closed on " + event.getEffectiveDeadline() + ".";
    }

    // ---------------- Dashboard ----------------

    @GetMapping("/student/dashboard")
    public String dashboard(Model model, Principal principal) {

        String email = principal.getName();

        List<Registration> registrations = registrationRepository
            .findByEmailIgnoreCaseOrderByRegisteredAtDesc(email);

        Set<Long> registeredEventIds = registrations.stream()
            .map(Registration::getEventId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        // Events happening today or later (finished events are hidden)
        List<Event> events = eventRepository
            .findByPublishedTrueAndEventDateGreaterThanEqualOrderByEventDateAsc(
                LocalDate.now());

        eventService.attachRegistrationCounts(events);

        model.addAttribute("events", events);
        model.addAttribute("studentEmail", email);
        model.addAttribute("registrations", registrations);
        model.addAttribute("registeredEventIds", registeredEventIds);

        model.addAttribute("notifications",
            notificationRepository
                .findTop20ByUserEmailIgnoreCaseOrderByCreatedAtDesc(email));
        model.addAttribute("unreadCount",
            notificationRepository
                .countByUserEmailIgnoreCaseAndSeenFalse(email));

        return "student-dashboard";
    }

    // ---------------- Notifications ----------------

    // Used by the popup: the page asks this every few seconds
    @GetMapping("/student/notifications/unread")
    @ResponseBody
    public Map<String, Object> unreadNotifications(Principal principal) {

        String email = principal.getName();

        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd MMM, HH:mm");

        List<Map<String, Object>> items = notificationRepository
            .findTop20ByUserEmailIgnoreCaseAndSeenFalseOrderByCreatedAtDesc(email)
            .stream()
            .map((Notification n) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", n.getId());
                item.put("message", n.getMessage());
                item.put("time", n.getCreatedAt() == null
                        ? "" : n.getCreatedAt().format(format));
                return item;
            })
            .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count",
            notificationRepository.countByUserEmailIgnoreCaseAndSeenFalse(email));
        body.put("items", items);

        return body;
    }

    @PostMapping("/student/notifications/read")
    public String markNotificationsRead(Principal principal) {

        notificationRepository.markAllSeen(principal.getName());

        return "redirect:/student/dashboard";
    }

    // ---------------- Event registration (login required) ----------------

    @GetMapping("/student/register/{eventId}")
    public String showRegistrationForm(
            @PathVariable Long eventId,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        Event event = eventRepository.findById(eventId)
            .filter(Event::isPublished)
            .orElse(null);

        if (event == null) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", "That event could not be found.");
            return "redirect:/student/dashboard";
        }

        if (event.isRegistrationClosed()) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", closedMessage(event));
            return "redirect:/student/dashboard";
        }

        AppUser student = userRepository
            .findByEmail(principal.getName().toLowerCase())
            .orElse(null);

        if (student == null) {
            return "redirect:/student/login?error=true";
        }

        // Full event: only students who are not registered yet are turned away
        eventService.attachRegistrationCounts(List.of(event));

        boolean alreadyRegistered = registrationRepository
            .existsByEmailIgnoreCaseAndEventId(student.getEmail(), eventId);

        if (event.isFull() && !alreadyRegistered) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Sorry, \"" + event.getEventName() + "\" is full.");
            return "redirect:/student/dashboard";
        }

        model.addAttribute("event", event);
        model.addAttribute("studentName", student.getFullName());
        model.addAttribute("studentEmail", student.getEmail());

        return "register";
    }

    @PostMapping("/student/register/{eventId}")
    public String registerForEvent(
            @PathVariable Long eventId,
            @RequestParam String department,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        Event event = eventRepository.findById(eventId)
            .filter(Event::isPublished)
            .orElse(null);

        if (event == null) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", "That event could not be found.");
            return "redirect:/student/dashboard";
        }

        if (event.isRegistrationClosed()) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", closedMessage(event));
            return "redirect:/student/dashboard";
        }

        AppUser student = userRepository
            .findByEmail(principal.getName().toLowerCase())
            .orElse(null);

        if (student == null) {
            return "redirect:/student/login?error=true";
        }

        Registration registration = new Registration();

        registration.setStudentName(student.getFullName());
        registration.setEmail(student.getEmail());
        registration.setDepartment(department.trim());
        registration.setEventId(event.getId());
        registration.setEventName(event.getEventName());

        boolean duplicate = false;
        boolean full = false;

        // Check and save together so the seat limit can never be exceeded
        synchronized (REGISTRATION_LOCK) {

            if (registrationRepository.existsByEmailIgnoreCaseAndEventId(
                    student.getEmail(), eventId)) {

                duplicate = true;

            } else if (event.getMaxSeats() != null
                    && registrationRepository.countByEventId(eventId)
                        >= event.getMaxSeats()) {

                full = true;

            } else {
                registrationRepository.save(registration);
            }
        }

        if (duplicate) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "You have already registered for this event!");
            return "redirect:/student/register/" + eventId;
        }

        if (full) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Sorry, \"" + event.getEventName() + "\" just became full.");
            return "redirect:/student/dashboard";
        }

        redirectAttributes.addFlashAttribute(
            "studentName", registration.getStudentName());
        redirectAttributes.addFlashAttribute(
            "eventName", registration.getEventName());

        return "redirect:/student/registration-success";
    }

    @GetMapping("/student/registration-success")
    public String registrationSuccess() {
        return "registration-success";
    }
}