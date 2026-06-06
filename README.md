# Groceries Tracker & Household Inventory Auditor

An intelligent, native Android application built using Kotlin and Jetpack Compose. This app serves as a smart grocery and household inventory tracker that audits pantry and fridge stock using the device camera and the **Gemini 3.5 Flash** model, and automatically synchronizes the resulting shopping list to **Google Tasks**.

## App Overview & Features

- **AI Vision Stock Auditor:** Snap a picture of your fridge or pantry. The integrated CameraX interface and Gemini 3.5 Flash model audit the image, identify the present items, estimate quantities, and detect missing items.
- **Automated Shopping List Generation:** Missing items are automatically flagged and placed on the local shopping list.
- **Cloud Task Synchronization:** Seamlessly syncs local shopping list items to a "Groceries" list in Google Tasks via the Google Tasks REST API.
- **Secure Storage:** Sensitive configuration data (such as the Gemini API key and OAuth account states) are safely stored locally using `EncryptedSharedPreferences`.
- **Modern Jetpack Compose UI:** Premium design with interactive states, micro-animations, and bottom navigation.

---

## Tech Stack

- **UI:** Jetpack Compose (Material 3, Navigation3, material-icons-extended)
- **Camera:** CameraX (Preview & ImageCapture JNI)
- **Database:** Room (SQLite) with KAPT annotation processing
- **AI Vision Engine:** Google AI Client SDK for Android (`gemini-3.5-flash`)
- **Cloud Integration:** Google Tasks API (REST) & Google Identity Services (OAuth 2.0 with play-services-auth)
- **Local Security:** EncryptedSharedPreferences (Android Security Crypto)
- **CI/CD:** GitHub Actions with automated APK compilation and GitHub Release drafting

---

## Architecture & Sync Logic

### Database-to-Cloud Synchronization
The sync model is designed to be reactive, offline-first, and self-healing.
1. When a user marks an item for shopping (`isInShoppingList = true`), the sync engine creates a new task in the user's "Groceries" list in Google Tasks and saves the resulting `googleTaskId` locally in the Room database.
2. When an item is marked as restocked (either manually or detected via the camera scanner), `isInShoppingList` becomes `false`. The sync engine uses the stored `googleTaskId` to mark the corresponding task as `completed` in the cloud, and then clears the `googleTaskId` locally.
3. **Offline Queueing:** If the device is offline, changes are kept locally. Once internet connectivity is restored, the `GoogleTasksSyncManager` scans the database for:
   - Items with `isInShoppingList == true` AND `googleTaskId == null` (needs creation)
   - Items with `isInShoppingList == false` AND `googleTaskId != null` (needs completion)
   This ensures that the local database itself acts as the sync queue, eliminating the need for a separate queue table.

### Gemini Prompt Structure
The scanner sends the captured image and the serialized list of currently known database items to the `gemini-3.5-flash` model with the following strict prompt:
> "Analyze this image of a fridge/pantry. Identify all items and their estimated quantities. Compare this against a provided list of 'known' items. Return a JSON array detailing what is present, what is new, and what known items appear to be missing."

---

## Setup Instructions

### 1. Get a Gemini API Key
1. Go to [Google AI Studio](https://aistudio.google.com/).
2. Create or select a project and generate a new **API Key**.
3. Once the app is compiled and running, enter this key on the **Settings** screen.

### 2. Configure Google Cloud Console for Google Tasks Sync
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project.
3. In the API Library, search for and enable the **Google Tasks API**.
4. Configure the **OAuth Consent Screen** (specify User Type, App Name, and support email).
5. **Create Credentials:**
   - Click **Create Credentials > OAuth client ID**.
   - **Android Client ID:**
     - Select **Android** as the application type.
     - Enter your package name: `com.example.groceriestracker`.
     - Enter your SHA-1 signing certificate fingerprint.
       - You can get your debug SHA-1 by running:
         ```bash
         keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
         ```
   - **Web Application Client ID (Required for Google Identity Sign-In):**
     - Select **Web application** as the application type.
     - Add authorized redirect URIs if prompted, or simply hit create.
     - Save the generated **Web Client ID**. (It will be automatically linked to your Android client ID inside Google Services).
6. Run the app, click "Sign in with Google" on the Settings screen, and approve the Tasks scope permission.

---

## UI Screenshots

Below are placeholders for the UI views of the application:

### AI Vision Camera Scanner
![AI Vision Scanner Screen](docs/screenshots/scanner_screen.png)

### Inventory Stock List
![Inventory Stock Screen](docs/screenshots/inventory_screen.png)

### Shopping List Checklists
![Shopping List Screen](docs/screenshots/shopping_list_screen.png)

### OAuth & Key Settings
![OAuth and Settings Screen](docs/screenshots/settings_screen.png)
