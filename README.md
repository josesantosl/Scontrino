# Scontrino

A personal receipt and loyalty card manager for Android. Built because every app that scans receipts requires a subscription, and every free alternative expects you to enter everything by hand.

## Why

Most receipt apps are built around a cloud AI core. That's why they need a subscription — they're renting compute to process your groceries. When the model gets more expensive, your plan gets more expensive. You're not buying software, you're renting a service you never fully control.

That design also turns the vendor's server into a single point of failure. If the service goes down, changes its pricing or shuts down, the app stops working — and your receipts may go with it.

Scontrino has no such dependency. OCR runs on-device with Tesseract and OpenCV, parsing is rule-based rather than model-based, and there are no API calls, no accounts and no subscription. Your data stays on your phone.

Your software, your data, your rules.

## Owes nothing to Google

No Google Play Services, no ML Kit, no Firebase, no analytics, no proprietary Google libraries of any kind. The app doesn't even request internet access — the only permission it asks for is the camera.

Everything it depends on is open source: OCR is done by Tesseract, image preprocessing by OpenCV, barcodes by ZXing. It runs the same on a de-Googled phone as on any other Android device.

## What it does

- Scan receipts with the camera and store them, with automatic product categorization
- Review every scan next to the photo: each item shows the receipt line it was read from
- See at a glance what the app corrected, like a misread name or a price adjusted to match the total
- Product aliases, so the same product matches across supermarkets even when names differ
- Price comparison between stores and spending stats
- Keep your loyalty cards in one place with barcode display at checkout
- Quick access to your payment app from the wallet
- Color-code receipts by store automatically when you add a loyalty card
- Works fully offline

## Stack

- Kotlin
- Jetpack Compose
- Room
- CameraX
- Tesseract (tesseract4android)
- OpenCV
- ZXing

## Architecture

MVVM with Repository pattern. Data layer is separated from UI, ViewModels handle business logic, Room manages local persistence.

## Status

Active development. Preparing the first release on F-Droid and Google Play, currently working on the store screenshots.

## License

© 2026 José Santos L. All rights reserved.

The source code is publicly available for reference and portfolio purposes only. No license is granted to use, copy, modify, or redistribute it.


---


made in Turin ♥
