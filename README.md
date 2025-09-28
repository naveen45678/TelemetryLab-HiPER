Telemetry Lab — README (1 page)

Goal
----
Build a Compose screen that simulates telemetry frames (20 Hz), performs CPU-heavy work off the main thread per frame, runs inside a foreground job/service, and reports jank% (last 30s). Adapt for Battery Saver.

What I implemented
------------------
- Compose UI: Start/Stop, Compute Load slider (0..8), stats panel (Jank %, FPS).
- Foreground Service (`TelemetryService`) that:
  - Runs a frame loop at 20 Hz (reduces to 10 Hz if Battery Saver ON).
  - Executes deterministic CPU workload on `Dispatchers.Default` for each frame (controlled by slider).
  - Publishes metrics via local broadcast; UI collects and displays them.
  - Uses `startForeground()` with a notification and `android:foregroundServiceType="dataSync|sensor"` in manifest.
- Jank monitoring: rolling 30s window; jank defined as frames exceeding 1.5× target interval (50ms for 20 Hz).
- Battery Saver handling: if power-save on, reduce rate to 10Hz and decrement compute load by 1.
- Macrobenchmark module (optional) and baseline profile packaging instructions included.

How to run
----------
1. Open in Android Studio (use API 33/34 emulator or device).
2. Run the app on a device, open Telemetry screen, press Start.
3. Change compute load via slider. Observe Jank% and FPS in the stats card.
4. To test Battery Saver behavior: enable Power Save in device; start the service and verify rate reduces.

Notes & tuning tips
-------------------
- Keep all heavy work off the main thread (use `Dispatchers.Default` or a dedicated thread pool).
- Avoid allocating new large objects per frame; reuse buffers and objects.
- If jank > 5% at load=2: lower per-frame allocations, chunk work across frames, or reduce per-frame compute and aggregate over time.

Files of interest
-----------------
- `ui/TelemetryScreen.kt` — Compose UI
- `service/TelemetryService.kt` — foreground loop + CPU work
- `util/CPULoad.kt` — deterministic load function
- `util/JankMonitor.kt` — rolling jank calculation
