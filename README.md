# Paceometer Android
**Correcting the Time-Saving Bias through Hyperbolic Pace Visualization**

Based on the research paper: *[“The Paceometer: A New Design for Speedometers to Help Drivers Make Better Decisions”](http://journal.sjdm.org/12/121007/jdm121007.pdf)* (Peer & Gamliel, 2013).

## 🧠 The Concept
Standard speedometers are linear (mph or km/h). However, the relationship between speed and time is **hyperbolic**. Most drivers suffer from the "time-saving bias," where they overestimate time saved at high speeds and underestimate it at low speeds.

The **Paceometer** displays speed in **Minutes per 10 Units** (miles or kilometers). This linearizes the time-saving calculation for the driver, making it immediately obvious that increasing speed from 100 to 110 km/h saves significantly less time than increasing from 30 to 40 km/h.

## 📱 Features
* **Real-time GPS Tracking:** High-accuracy speed detection using Google Play Services.
* **Dual-Unit Support:** Toggle between Metric (km/h) and Imperial (mph) systems.
* **Hyperbolic Gauge:** A custom-drawn UI component that displays "Minutes per 10 [Units]" alongside traditional speed.
* **Dynamic Calculations:** Real-time updates on time saved per distance.

## 🛠 Tech Stack
* **Language:** Kotlin
* **Platform:** Android (Min SDK 21)
* **Location API:** `com.google.android.gms:play-services-location` (FusedLocationProviderClient)
* **UI:** Custom Canvas Drawing for the Paceometer Gauge.

## 🚀 How Speed is Achieved
This app utilizes the **Fused Location Provider Client**. Unlike the standard Android `LocationManager`, the Fused API intelligently combines GPS, Wi-Fi, and Cell signals to provide the highest accuracy with optimized battery usage.

1.  **Requesting Permissions:** The app requires `ACCESS_FINE_LOCATION`.
2.  **Location Request Settings:** * Interval: 1000ms (1 second updates).
    * Priority: `PRIORITY_HIGH_ACCURACY`.
3.  **The Calculation:**
    * The API returns speed in meters per second ($m/s$).
    * **To km/h:** $v_{km/h} = v_{m/s} \times 3.6$
    * **To mph:** $v_{mph} = v_{m/s} \times 2.23694$
    * **Pace:** $Pace = 600 / v_{unit}$ (Minutes to cover 10 units).

## 🏗 Installation
1. Clone the repository.
2. Open in **Android Studio**.
3. Sync Gradle and build the APK.
4. **Note:** Speed tracking will not work on an emulator unless you manually mock GPS routes. For best results, test on a physical device outdoors.
