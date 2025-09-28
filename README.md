# Telemetry Lab

## Goal
Build a Compose screen that simulates telemetry frames (20 Hz), performs CPU-heavy work off the main thread per frame, runs inside a foreground service, and reports jank% (last 30s). Adapt behavior for Battery Saver.

---

## What I Implemented

- **Compose UI:**
  - Start/Stop buttons
  - **Dynamic** Compute Load slider (0–8) - works while service is running
  - Stats panel showing **Jank %** and **FPS**
  - Real-time battery saver status display
  - Shows both requested load and effective load

- **Foreground Service (`TelemetryService`):**
  - Frame loop at 20 Hz (reduces to 10 Hz if Battery Saver ON)
  - Executes deterministic CPU workload on `Dispatchers.Default` per frame
  - **Dynamic load updates** - slider changes apply immediately while running
  - Real-time battery saver detection via broadcast + polling backup
  - Publishes metrics via local broadcast; UI collects and displays them
  - Uses `startForeground()` with notification showing current load status
  - `android:foregroundServiceType="dataSync"` for Android 14+ compliance

- **Jank Monitoring:**
  - Rolling **30-second window** (not 30 frames)
  - Jank defined as frames exceeding 1.5× target interval (50ms for 20 Hz, 100ms for 10 Hz)
  - Thread-safe metrics calculation with synchronized access

- **Battery Saver Handling:**
  - **Automatic detection** via `PowerManager` broadcast + polling fallback
  - Reduces frame rate from 20 Hz → 10 Hz
  - Decreases compute load by 1 (minimum 0)
  - UI shows FPS as "-" during battery saver mode
  - Real-time toast notifications when battery saver toggles

- **Dynamic Features:**
  - Slider updates service load **while running** via broadcast mechanism
  - Battery saver changes detected and applied immediately
  - Notification updates in real-time to show current vs effective load
  - Thread-safe implementation using `AtomicInteger` and `@Volatile`

---

## How to Run

1. Open project in Android Studio (API 33/34 recommended)
2. Run app on device/emulator
3. Open Telemetry screen, press **Start**
4. **While running**: adjust compute load via slider - changes apply immediately
5. Test Battery Saver: 
   - Enable via Settings → Battery → Battery Saver, OR
   - Use ADB: `adb shell settings put global low_power 1`
   - Observe: Rate drops to 10 Hz, load reduces by 1, toast notification appears

---

## Performance Target

- **Target: ≤5% jank at load=2** (normal conditions, battery saver off)
- Monitor via stats panel in real-time
- Notification shows: "Load: 2→1 | Rate: 10 Hz" during battery saver

---

## Architecture Highlights

- **Thread Safety**: `AtomicInteger` for load updates, synchronized frame timing data
- **Dynamic Updates**: Service responds to broadcasts for immediate load changes
- **Robust Battery Detection**: Dual approach (broadcasts + polling) for reliability
- **Clean Separation**: Service handles compute, UI handles display, ViewModel bridges

---

## Files of Interest

- `ui/TelemetryScreen.kt` — Dynamic Compose UI with real-time updates
- `service/TelemetryService.kt` — Foreground service with dynamic load handling
- `data/TelemetryViewModel.kt` — State management with dynamic updates
- `util/CPULoad.kt` — Deterministic CPU workload generator

---

## Testing Battery Saver

```bash
# Enable battery saver
adb shell settings put global low_power 1

# Disable battery saver  
adb shell settings put global low_power 0

# Check current state
adb shell settings get global low_power

# View service logs
adb logcat -s TelemetryService
```

---

## Key Improvements Over Basic Requirements

1. **Dynamic Load Control** - Slider works while service runs (not just at start)
2. **Real-time Battery Saver** - Instant detection and adaptation 
3. **Enhanced UI Feedback** - Shows effective vs requested load
4. **Robust Implementation** - Thread-safe with fallback mechanisms
5. **Modern Android Compliance** - Proper foreground service types for Android 14+
