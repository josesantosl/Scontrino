# Scontrino


A personal receipt and loyalty card manager for Android. Built because every existing app either costs too much, requires an internet connection, or treats a supermarket trip like a corporate expense report.


## Why


Most receipt apps are built around an AI core. That's why they need a subscription — they're renting compute to process your groceries. When the model gets more expensive, your plan gets more expensive. You're not buying software, you're renting a service you never fully control.


Scontrino runs on your device. No API calls, no cloud dependency, no subscription. OCR runs on-device with ML Kit. Parsing is rule-based, not model-based. Your data stays on your phone. It works in the subway, in the mountains, or anywhere your signal doesn't.


The app you install today works the same in five years. Your software, your data, your rules.


## What it does


- Scan and store shopping receipts with automatic product categorization
- Keep your loyalty cards in one place with barcode display at checkout
- Color-code receipts by store automatically when you add a loyalty card
- Works fully offline


## Stack


Kotlin · Jetpack Compose · Room · CameraX · ML Kit


## Architecture


MVVM with Repository pattern. Data layer is separated from UI, ViewModels handle business logic, Room manages local persistence.


## Status


Active development. Stats screen in progress.


---


made in Turin ♥
