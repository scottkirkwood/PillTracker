# Pill & Supplement Tracker (Native Android App)

An offline-first Android application designed for reliable medication reminders with **two-stage escalation**, direct notification actions, and seamless cloud synchronization with [forusers.com](https://forusers.com/pills).

---

## 🌟 Key Architecture & Features

### 1. Two-Stage Escalation Reminder System
* **Stage 1 (Scheduled Time e.g., 7:30 AM / 8:00 PM):**
  * Exact alarm triggers via Android `AlarmManager.setExactAndAllowWhileIdle`.
  * Posts a **Quiet Notification** on a dedicated silent channel with custom action buttons: `[ Done ]` and `[ Remind in 1h ]`.
* **Stage 2 (Escalation Alarm - 60 Minutes Later):**
  * If the quiet reminder was ignored or not marked done after 60 minutes, Android automatically triggers an **Escalated Audible Alarm** (`STREAM_ALARM`) with vibration and a heads-up banner to ensure important prescriptions are never missed.
* **Instant Background Actions (`PillActionReceiver`):**
  * Tapping **"Done"** directly in the notification shade records the intake event in the local database, cancels the Stage 2 alarm, dismisses the notification, and queues a cloud sync—even if the app is closed.

---

### 2. Source of Truth & Offline Reliability
* **Medication Definitions & Schedules:** `forusers.com` is the **Source of Truth**.
* **Intake Execution Logs:** The Android app is the **Source of Truth** (local-first SQLite/JSON cache).
* **7-Day Offline Cache Grace Period:**
  * The app operates 100% offline using its local cached configuration.
  * If the device has been offline for more than 7 days, a warning badge alerts the user to reconnect and sync.

---

### 3. Preloaded Medications & Routines

#### 🌅 Morning Stack (~7:30 AM)
* **Candesartan 8mg** — 🚨 *Doctor's prescription (High Importance / Requires Alarm Escalation)*
* **Curcumin 300mg** — *Better with fat / meal*
* **K2 120mcg** — *Fat-soluble*
* **Vitamin C 600mg**
* **Zinc 2mg** — *Take with food*
* **D3 1000IU** — *Fat-soluble*
* **Berberine 500mg** — *Take before/with meal*
* **Omega 3 Fish Oil 2 × 1g** — *Take with meal*
* **Turmeric 500mg**
* **NMN 500mg** — *Morning energy*

#### 🌙 Evening Stack (~8:00 PM)
* **Rosuvastatin 10mg** — 🚨 *Doctor's prescription (High Importance / Requires Alarm Escalation)*
* **Coenzyme Q10 200mg** — *Better with fat / meal*
* **Magnesium Bisglycinate 2 × 200mg** — *Pre-bed relaxation*
* **Melatonin 2mg** — *Timed release (30–60m before bed)*

#### ⚡ Quick / Ad-hoc Logging (No scheduled alarms)
* **Creatine 5g** — *One-tap button: "In Morning Coffee"*
* **Glycine 1500mg** — *One-tap button: "In Evening Tea"*
* **Dog Pill** — *One-tap toggle: "Daily Dog Pill"*

---

## 📱 Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Background Scheduling:** Android `AlarmManager` (Exact & Doze-compliant) + `WorkManager`
* **Local Persistence:** Local structured JSON store with StateFlow reactive streams
* **Cloud API:** Connects to `https://forusers.com/api/pills/*`

---

## 🚀 Building & Running

1. Open Android Studio.
2. Select **Open** and choose `/home/scott/AndroidStudioProjects/PillTracker`.
3. Allow Gradle to sync.
4. Run on your Android device or emulator.
5. In **Settings**, you can use the diagnostic buttons to test both the **Stage 1 (Quiet)** and **Stage 2 (Escalated Alarm)** notifications immediately.
