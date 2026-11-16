package com.college.collegeevent;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.college.collegeevent.repository.EventRepository;

@Controller
public class HomeController {

    private final EventRepository eventRepository;

    public HomeController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @GetMapping("/")
    public String home(Model model) {

        // Only upcoming events (finished events are hidden)
        model.addAttribute(
            "events",
            eventRepository
                .findByPublishedTrueAndEventDateGreaterThanEqualOrderByEventDateAsc(
                    LocalDate.now())
        );

        return "index";
    }
}