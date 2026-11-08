# Scontrino

A personal receipt and loyalty card manager for Android. Built because every app that scans receipts requires a subscription, and every free alternative expects you to enter everything by hand.

## Why


Most receipt apps are built around an AI core. That's why they need a subscription — they're renting compute to process your groceries. When the model gets more expensive, your plan gets more expensive. You're not buying software, you're renting a service you never fully control.


Scontrino runs on your device. No API calls, no cloud dependency, no subscription. OCR runs on-device with Tesseract and OpenCV. Parsing is rule-based, not model-based. Your data stays on your phone. It works in the subway, in the mountains, or anywhere your signal doesn't.


The app you install today works the same in five years. Your software, your data, your rules.


## Owes nothing to Google


No Google Play Services, no ML Kit, no Firebase, no analytics, no proprietary Google libraries of any kind. The app doesn't even request internet access — the only permission it asks for is the camera.


Everything it depends on is open source: OCR is done by Tesseract, image preprocessing by OpenCV, barcodes by ZXing. It runs the same on a de-Googled phone as on any other Android device.


## What it does


- Scan and store shopping receipts with automatic product categorization
- Keep your loyalty cards in one place with barcode display at checkout
- Color-code receipts by store automatically when you add a loyalty card
- Works fully offline


## Stack


Kotlin · Jetpack Compose · Room · CameraX · Tesseract (tesseract4android) · OpenCV · ZXing


## Architecture


MVVM with Repository pattern. Data layer is separated from UI, ViewModels handle business logic, Room manages local persistence.


## Status


Active development. Stats screen in progress.


## License


© 2026 José Santos L. All rights reserved.

The source code is publicly available for reference and portfolio purposes only. No license is granted to use, copy, modify, or redistribute it.


---


made in Turin ♥
