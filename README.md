# Attendance App 📸🏫

A secure and efficient Android application designed for schools to manage teacher attendance using **Face Detection** and **PIN Verification**.

## 🚀 Key Features

### 👤 Profile-Based Access
- **Principal (Admin) Mode:** Principal can manage the entire staff, view comprehensive attendance reports, and add new teachers.
- **Teacher Mode:** Simplified interface for daily check-in and check-out.

### 📷 Smart Attendance Capture
- **Face Detection:** Integrated with **Google ML Kit** to ensure a face is present before capturing attendance.
- **Full-Screen Camera:** Immersive camera experience for high-quality photo evidence.
- **Dual Verification:** Combines face detection with a secure 4-digit PIN to prevent proxy attendance.

### 🛡️ Secure PIN Management
- **Monthly Restrictions:** Teachers can change their PIN only once every 30 days.
- **Master PIN:** The Principal has a master PIN for emergency overrides and identity verification.
- **Admin Reset:** The Principal can reset any teacher's PIN directly from the dashboard.

### 📊 Attendance Insights
- **Photo Logs:** Every check-in and check-out is stored with a timestamp and a photo.
- **Principal Reports:** A unified view for the Principal to see all staff activity for any selected date.
- **Auto-Cleanup:** Automatically deletes records and photos older than 45 days to save device storage.

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Android XML with ViewBinding
- **Camera:** CameraX API
- **AI/ML:** Google ML Kit (Face Detection)
- **Database:** Room Persistence Library
- **Concurrency:** Kotlin Coroutines & Flow
- **Architecture:** MVVM (Model-View-ViewModel) logic

## 📸 Screen Guide

1. **Homepage:** Select your profile from the dropdown.
2. **Attendance:** Click "Check In" or "Check Out" to open the full-screen camera.
3. **Verification:** Align your face. Once detected, enter your PIN to save.
4. **History:** Tap any date on the calendar to view your records and photos.
5. **Admin:** (Principal Only) Use the "Add Teacher" button to expand your staff list.

## ⚙️ Setup & Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/ayushv371/AttendanceApp.git
   ```
2. Open the project in **Android Studio (Koala or newer)**.
3. Ensure you have **JDK 17+** configured.
4. Sync Gradle and run the app on a physical device (recommended for Camera/ML Kit features).


*Developed with ❤️ by Ayush.*
