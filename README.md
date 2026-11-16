# CampusConnect

A college event management web application. Students register for events
and get notifications; organizers publish and manage events.

## Features
- Separate student and organizer logins (Spring Security, BCrypt)
- Main organizer can add and remove other organizers
- Popup notifications when events are published or changed
- Automatic reminders before registration deadlines and events
- Seat limits and registration deadlines for each event
- Edit and delete events

## Tech Stack
Java 21, Spring Boot 3, Spring Security, Spring Data JPA (Hibernate),
Thymeleaf, MySQL, HTML/CSS/JavaScript

## How to Run
1. Install Java 21 and MySQL.
2. Create the database: `CREATE DATABASE collegeEvent;`
3. In `src/main/resources`, create `application.properties` and add your
   MySQL login, organizer email and password.
4. Run `CollegeEventApplication` and open http://localhost:8080
