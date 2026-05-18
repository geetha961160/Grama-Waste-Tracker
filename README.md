# Grama Waste Tracker 🚛♻️

Grama Waste Tracker is a smart village waste management Android application developed using Kotlin and Jetpack Compose. The application helps citizens and local authorities monitor waste collection activities, report garbage blackspots, and improve waste segregation awareness in rural areas.

## Problem Statement

Many villages face challenges in proper waste collection, illegal dumping, delayed garbage pickup, and lack of waste segregation awareness. This project provides a digital solution to improve village sanitation management and communication between citizens and administrators.

## Objectives

- Digitize village waste management
- Enable live tracking of garbage collection vehicles
- Allow citizens to report waste blackspots
- Improve waste segregation awareness
- Support local language accessibility for rural users

## Features Implemented

### Citizen/User Features
- Live tractor tracking with simulated movement
- Arrival alert for waste collection vehicle
- Blackspot reporting with location and issue details
- Waste segregation guide
- Kannada language support

### Admin Features
- Admin dashboard
- View operational metrics
- Track daily waste collection
- Monitor complaints and citizen reports
- Collection performance

## Tech Stack

- Kotlin
- :contentReference[oaicite:0]{index=0}
- :contentReference[oaicite:1]{index=1}
- Material Design Components
- Google Maps (Simulated tracking)

## Project Architecture

This project follows MVVM architecture:

UI Layer → ViewModel → Data Layer

This architecture improves maintainability, scalability, and code organization.

## Project Structure

```plaintext
GramaWasteTracker/
│
├── app/
├── ui/
├── screens/
├── models/
├── utils/
├── screenshots/
├── README.md
└── build.gradle.kts
```

## Installation and Setup

### Prerequisites

- :contentReference[oaicite:2]{index=2} installed
- Android SDK installed
- Emulator or Android device

### Steps to Run

1. Clone the repository

```bash
git clone your-github-repository-link
```

2. Open the project in Android Studio

3. Let Gradle sync complete

4. Run the app on emulator or physical device

## Future Enhancements

- Firebase integration for cloud storage
- Real GPS-based vehicle tracking
- Push notifications for collection alerts
- Complaint status tracking
- Driver attendance management

## Real World Impact

This project supports smart village development and promotes clean and sustainable waste management practices. It aligns with digital governance and sanitation initiatives in rural communities.

## Author

Developed as an academic software project for smart rural waste management.
