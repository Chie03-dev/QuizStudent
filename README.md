# Quiz Student App (Android)

An offline, LAN-only Android client for the Quiz system, built with Kotlin and Jetpack Compose. This application is the student client child app for the main desktop application: [Quiz Desktop App](https://github.com/Chie03-dev/Quiz).

## Features
- Local LAN connection over WebSocket to the instructor server.
- Service discovery via mDNS / DNS-SD or QR code scanning.
- Support for multiple question types (MCQ, True/False, Identification, Fill-in, Enumeration, Problem, Matching, Connect).
- In-composition UI for all interactive elements to prevent window focus loss and student app pause triggers.
- Robust state restoration and queue management.

## Tech Stack
- I made the logo in Canva
- Kotlin & Jetpack Compose (Material 3)
- OkHttp WebSocket
- NsdManager for local network discovery
- CameraX and ML Kit barcode scanner

## Requirements
- Android 14.0 (API level 34) or higher.
- Local network connection to the quiz instructor server.
