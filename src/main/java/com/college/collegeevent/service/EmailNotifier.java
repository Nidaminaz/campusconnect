package com.college.collegeevent.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailNotifier {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    public EmailNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendNewEventEmails(List<String> recipients,
                                   String eventName,
                                   String eventDate,
                                   String venue,
                                   String description) {

        for (String recipient : recipients) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();

                message.setFrom(senderEmail);
                message.setTo(recipient);
                message.setSubject("CampusConnect - New Event: " + eventName);
                message.setText(
                        "Hello,\n\n"
                        + "A new event has been scheduled on CampusConnect.\n\n"
                        + "Event : " + eventName + "\n"
                        + "Date  : " + eventDate + "\n"
                        + "Venue : " + venue + "\n\n"
                        + description + "\n\n"
                        + "Log in to CampusConnect to register.");

                mailSender.send(message);

            } catch (Exception e) {
                System.err.println("Could not send email to "
                        + recipient + ": " + e.getMessage());
            }
        }
    }
}