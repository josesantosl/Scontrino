# Scontrino - Receipt and Loyalty Card Manager

Scontrino is a modern Android application designed to help users manage their shopping receipts and loyalty cards in one place. It features automatic product categorization, barcode scanning, and visual synchronization between store cards and receipts.

## Project Structure

The project follows a standard MVVM (Model-View-ViewModel) architecture with a Repository pattern for data management.

### 1. Data Layer (`com.pepesantos.scontrino.data`)

#### Models (`.model`)
*   **Receipt**: Represents a single shopping trip, linked to a Store.
*   **Store**: Represents a retail location, including its name and a custom color.
*   **Product**: Represents a unique item that can be bought, linked to a Category.
*   **Item**: The join entity between a Receipt and a Product, storing specific price and quantity.
*   **Category**: Pre-defined or custom categories (e.g., "Vegetables", "Pets") with icons and colors.
*   **LoyaltyCard**: Stores card numbers linked to a specific Store for easy access at checkout.
*   **ItemEntry**: A UI-friendly helper class for managing item input during receipt creation.

#### DAOs (`.dao`)
Standard Room DAOs providing CRUD operations. notable mentions:
*   **ReceiptDao**: Includes complex JOIN queries to fetch receipts enriched with store names and colors.
*   **LoyaltyCardDao**: Fetches cards along with their linked store details.

#### Database (`AppDatabase.kt`)
*   Manages the Room database instance.
*   Handles **destructive migrations** and robust **pre-population** of default categories during initial setup.

### 2. UI Layer (`com.pepesantos.scontrino.ui`)

#### Screens (`.screens`)
*   **ReceiptsScreen**: The main dashboard showing a chronological list of receipts, color-coded by store.
*   **AddReceiptScreen**: A dynamic form for entering new receipts. Includes automatic category detection as you type and a quantity counter.
*   **WalletScreen**: A digital wallet for loyalty cards. Features long-press to delete and "With ♥️ from Turin" branding.
*   **StatsScreen**: (In progress) Dashboard for spending analysis.

#### ViewModels (`.viewmodel`)
*   **ReceiptViewModel**: Coordinates receipt saving, loading, and deletion. Handles the complex logic of matching typed store names to existing entities.
*   **WalletViewModel**: Manages loyalty cards and ensures store colors are synced when a card is added.
*   **CategoryMatcher**: A utility that automatically detects the correct category for a product based on keywords (e.g., "tofu" -> "plant_protein").

#### Components (`.components`)
*   **BarcodeScanner**: A CameraX + ML Kit integration that allows scanning physical cards. It includes smart logic to preserve leading zeros in EAN-13 codes.

### 3. Key Features Documentation

#### Automatic Color Sync
When a user adds a loyalty card with a specific color to a store (e.g., Red for Bennet), all past and future receipts for that store will automatically adopt that color in the main list.

#### Smart Input
The `AddReceiptScreen` is optimized for speed:
*   Typing a name triggers the `CategoryMatcher`.
*   A new empty item row is added automatically when the last one is filled.
*   Prices support both dot and comma separators for global usability.

#### Barcode Integrity
The `BarcodeScanner` automatically converts 12-digit UPC-A codes to 13-digit EAN-13 by adding a leading zero, ensuring compatibility with European supermarket scanners.

## Technical Details
*   **Language**: Kotlin
*   **UI**: Jetpack Compose
*   **Database**: Room
*   **Camera**: CameraX
*   **Barcode Detection**: Google ML Kit
*   **Unit Testing**: Mockito-Kotlin + Coroutines Test
