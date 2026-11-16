package com.college.collegeevent.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.college.collegeevent.AppUser;
import com.college.collegeevent.Event;
import com.college.collegeevent.Registration;
import com.college.collegeevent.repository.AppUserRepository;
import com.college.collegeevent.repository.EventRepository;
import com.college.collegeevent.repository.RegistrationRepository;
import com.college.collegeevent.service.EventNotificationService;
import com.college.collegeevent.service.EventService;

@Controller
@RequestMapping("/organizer")
public class OrganizerController {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final EventNotificationService notificationService;
    private final EventService eventService;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // The main organizer (admin) email from application.properties
    @Value("${app.organizer.email}")
    private String mainOrganizerEmail;

    public OrganizerController(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            EventNotificationService notificationService,
            EventService eventService,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.notificationService = notificationService;
        this.eventService = eventService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Only the main organizer may add or remove organizers
    private boolean isMainOrganizer(Principal principal) {
        return principal.getName().equalsIgnoreCase(mainOrganizerEmail.trim());
    }

    // An organizer can change their own events; the main organizer can change all
    private boolean canManage(Principal principal, Event event) {
        return isMainOrganizer(principal)
                || (event.getOrganizerEmail() != null
                    && event.getOrganizerEmail().equalsIgnoreCase(principal.getName()));
    }

    private void loadOrganizers(Model model) {
        model.addAttribute("organizers", userRepository.findByRole("ORGANIZER"));
        model.addAttribute("mainEmail", mainOrganizerEmail.trim());
    }

    // Empty text means "no date"
    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text.trim());
    }

    // Returns an error message, or null when the deadline is fine
    private String deadlineError(LocalDate eventDate,
                                 LocalDate deadline,
                                 boolean mustNotBeInPast) {

        if (deadline == null) {
            return null;
        }

        if (deadline.isAfter(eventDate)) {
            return "The registration deadline cannot be after the event date.";
        }

        if (mustNotBeInPast && deadline.isBefore(LocalDate.now())) {
            return "The registration deadline cannot be in the past.";
        }

        return null;
    }

    // ---------------- Dashboard ----------------

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {

        boolean main = isMainOrganizer(principal);

        // Main organizer sees every event, others see only their own
        List<Event> events = main
            ? eventRepository.findAll(Sort.by(Sort.Direction.ASC, "eventDate"))
            : eventRepository.findByOrganizerEmailOrderByEventDateAsc(
                principal.getName());

        eventService.attachRegistrationCounts(events);

        model.addAttribute("events", events);
        model.addAttribute("canAddOrganizer", main);
        model.addAttribute("showOwner", main);

        return "organizer-dashboard";
    }

    // ---------------- Add event ----------------

    @GetMapping("/add-event")
    public String addEventForm() {
        return "add-event";
    }

    @PostMapping("/add-event")
    public String addEvent(
            @RequestParam String eventName,
            @RequestParam String description,
            @RequestParam String eventDate,
            @RequestParam String venue,
            @RequestParam(required = false) Integer maxSeats,
            @RequestParam(required = false) String registrationDeadline,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (maxSeats != null && maxSeats < 1) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Seat limit must be at least 1, or leave it empty for no limit.");
            return "redirect:/organizer/add-event";
        }

        LocalDate date = LocalDate.parse(eventDate);
        LocalDate deadline = parseDate(registrationDeadline);

        String error = deadlineError(date, deadline, true);

        if (error != null) {
            redirectAttributes.addFlashAttribute("errorMessage", error);
            return "redirect:/organizer/add-event";
        }

        Event event = new Event();

        event.setEventName(eventName.trim());
        event.setDescription(description.trim());
        event.setEventDate(date);
        event.setVenue(venue.trim());
        event.setMaxSeats(maxSeats);
        event.setRegistrationDeadline(deadline);
        event.setOrganizerEmail(principal.getName());
        event.setPublished(true);

        Event saved = eventRepository.save(event);

        int notified = notificationService.notifyStudentsAboutNewEvent(saved);

        redirectAttributes.addFlashAttribute(
            "successMessage",
            "Event published successfully! " + notified
                + " student(s) have been notified."
        );

        return "redirect:/organizer/dashboard";
    }

    // ---------------- Edit event ----------------

    @GetMapping("/events/{id}/edit")
    public String editEventForm(
            @PathVariable Long id,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        Event event = eventRepository.findById(id).orElse(null);

        if (event == null || !canManage(principal, event)) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Event not found, or you are not allowed to change it.");
            return "redirect:/organizer/dashboard";
        }

        eventService.attachRegistrationCounts(List.of(event));

        model.addAttribute("event", event);

        return "edit-event";
    }

    @PostMapping("/events/{id}/edit")
    public String editEvent(
            @PathVariable Long id,
            @RequestParam String eventName,
            @RequestParam String description,
            @RequestParam String eventDate,
            @RequestParam String venue,
            @RequestParam(required = false) Integer maxSeats,
            @RequestParam(required = false) String registrationDeadline,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        Event event = eventRepository.findById(id).orElse(null);

        if (event == null || !canManage(principal, event)) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Event not found, or you are not allowed to change it.");
            return "redirect:/organizer/dashboard";
        }

        if (maxSeats != null && maxSeats < 1) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Seat limit must be at least 1, or leave it empty for no limit.");
            return "redirect:/organizer/events/" + id + "/edit";
        }

        LocalDate date = LocalDate.parse(eventDate);
        LocalDate deadline = parseDate(registrationDeadline);

        // A past deadline is allowed here: it closes registration right away
        String error = deadlineError(date, deadline, false);

        if (error != null) {
            redirectAttributes.addFlashAttribute("errorMessage", error);
            return "redirect:/organizer/events/" + id + "/edit";
        }

        long registered = registrationRepository.countByEventId(id);

        if (maxSeats != null && maxSeats < registered) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Seat limit cannot be less than the " + registered
                    + " student(s) who already registered.");
            return "redirect:/organizer/events/" + id + "/edit";
        }

        boolean notified = eventService.updateEvent(
            event,
            eventName.trim(),
            description.trim(),
            date,
            venue.trim(),
            maxSeats,
            deadline);

        redirectAttributes.addFlashAttribute(
            "successMessage",
            notified
                ? "Event updated. Registered students have been notified."
                : "Event updated.");

        return "redirect:/organizer/dashboard";
    }

    // ---------------- Delete event ----------------

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        Event event = eventRepository.findById(id).orElse(null);

        if (event == null || !canManage(principal, event)) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Event not found, or you are not allowed to delete it.");
            return "redirect:/organizer/dashboard";
        }

        String name = event.getEventName();
        int notified = eventService.deleteEvent(event);

        redirectAttributes.addFlashAttribute(
            "successMessage",
            "Event \"" + name + "\" was deleted. " + notified
                + " registered student(s) were notified.");

        return "redirect:/organizer/dashboard";
    }

    // ---------------- Registrations ----------------
    // Main organizer: ALL registrations (with the owner of each event)
    // Other organizers: only registrations for their own events

    @GetMapping("/registrations")
    public String registrations(Principal principal, Model model) {

        boolean main = isMainOrganizer(principal);

        List<Registration> registrations;

        if (main) {

            registrations = registrationRepository
                .findAll(Sort.by(Sort.Direction.DESC, "registeredAt"));

            Map<Long, String> eventOrganizers = new HashMap<>();

            for (Event e : eventRepository.findAll()) {
                eventOrganizers.put(
                    e.getId(),
                    e.getOrganizerEmail() == null ? "-" : e.getOrganizerEmail());
            }

            model.addAttribute("eventOrganizers", eventOrganizers);

        } else {

            List<Long> myEventIds = eventRepository
                .findByOrganizerEmailOrderByEventDateAsc(principal.getName())
                .stream()
                .map(Event::getId)
                .toList();

            registrations = myEventIds.isEmpty()
                ? List.of()
                : registrationRepository
                    .findByEventIdInOrderByRegisteredAtDesc(myEventIds);
        }

        model.addAttribute("registrations", registrations);
        model.addAttribute("showOrganizer", main);

        return "admin-registrations";
    }

    // ---------------- Add organizer (main organizer only) ----------------

    @GetMapping("/add-organizer")
    public String addOrganizerForm(
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!isMainOrganizer(principal)) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Only the main organizer can manage organizers.");
            return "redirect:/organizer/dashboard";
        }

        loadOrganizers(model);

        return "add-organizer";
    }

    @PostMapping("/add-organizer")
    public String addOrganizer(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!isMainOrganizer(principal)) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Only the main organizer can manage organizers.");
            return "redirect:/organizer/dashboard";
        }

        String cleanName = fullName == null ? "" : fullName.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();

        String error = null;

        if (cleanName.isEmpty()) {
            error = "Please enter the organizer's full name.";
        } else if (!cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            error = "Please enter a valid email address.";
        } else if (userRepository.existsByEmail(cleanEmail)) {
            error = "This email is already registered.";
        } else if (password == null || password.length() < 8) {
            error = "Password must contain at least 8 characters.";
        } else if (!password.equals(confirmPassword)) {
            error = "Passwords do not match.";
        }

        if (error != null) {
            model.addAttribute("error", error);
            loadOrganizers(model);
            return "add-organizer";
        }

        AppUser organizer = new AppUser();

        organizer.setFullName(cleanName);
        organizer.setEmail(cleanEmail);
        organizer.setPassword(passwordEncoder.encode(password));
        organizer.setRole("ORGANIZER");

        userRepository.save(organizer);

        redirectAttributes.addFlashAttribute(
            "successMessage",
            "Organizer account created for " + cleanEmail + ".");

        return "redirect:/organizer/add-organizer";
    }

    // ---------------- Remove organizer (main organizer only) ----------------

    @PostMapping("/delete-organizer/{id}")
    public String deleteOrganizer(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (!isMainOrganizer(principal)) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Only the main organizer can manage organizers.");
            return "redirect:/organizer/dashboard";
        }

        AppUser target = userRepository.findById(id).orElse(null);

        if (target == null || !"ORGANIZER".equals(target.getRole())) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", "Organizer account not found.");
            return "redirect:/organizer/add-organizer";
        }

        // The main organizer can never be removed
        if (target.getEmail().equalsIgnoreCase(mainOrganizerEmail.trim())) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "The main organizer account cannot be removed.");
            return "redirect:/organizer/add-organizer";
        }

        userRepository.delete(target);

        redirectAttributes.addFlashAttribute(
            "successMessage",
            "Organizer " + target.getEmail() + " has been removed.");

        return "redirect:/organizer/add-organizer";
    }
}