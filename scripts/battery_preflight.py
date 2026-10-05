#!/usr/bin/env python3
"""Static battery-regression checks for Task Tunnel.

This does not replace Android Studio Energy Profiler / batterystats on a physical device. It keeps
known high-cost patterns from quietly reappearing in source.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
service = (ROOT / "app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt").read_text()
notification = (ROOT / "app/src/main/java/com/example/tasktunnel/notification/TunnelNotificationController.kt").read_text()
main = (ROOT / "app/src/main/java/com/example/tasktunnel/MainActivity.kt").read_text()
usage = (ROOT / "app/src/main/java/com/example/tasktunnel/usage/SurfaceUsage.kt").read_text()

errors: list[str] = []
notes: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

for permission in (
    "android.permission.WAKE_LOCK",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.FOREGROUND_SERVICE",
):
    require(permission not in manifest, f"Unexpected battery-sensitive permission: {permission}")

for forbidden in ("AlarmManager", "WorkManager", "JobScheduler", "PowerManager.WakeLock"):
    hits = list((ROOT / "app/src/main/java").rglob("*.kt"))
    found = [str(p.relative_to(ROOT)) for p in hits if forbidden in p.read_text()]
    require(not found, f"Unexpected background scheduling/wake primitive {forbidden}: {found}")

require(main.count("collectAsStateWithLifecycle()") >= 3,
        "Main UI flows must use lifecycle-aware collection.")
require("if (!isWindowEvent && eventPackageName != null && eventPackageName !in TARGET_PACKAGES) return" in service,
        "Accessibility non-window events from unrelated apps are no longer rejected early.")
require("val desiredEventTypes = if (enabled) REQUESTED_ACCESSIBILITY_EVENT_TYPES else 0" in service,
        "Master OFF must unsubscribe from accessibility events.")
require("YOUTUBE_UNRESOLVED_PROBE_ATTEMPTS" in service,
        "YouTube unresolved subscription probing must remain bounded.")
require("if (!protectionEnabled || !deviceInteractionReady) return" in service,
        "Notification progress refresh must stop while the device is not interactive.")
require("RETENTION_PRUNE_INTERVAL_MILLIS" in usage and "scope.launch(Dispatchers.IO)" not in usage,
        "Surface usage writes must stay serialized and retention pruning must be batched.")

match = re.search(r"PROGRESS_REFRESH_MILLIS\s*=\s*([0-9_]+)L", notification)
if match:
    interval = int(match.group(1).replace("_", ""))
    require(interval >= 30_000, f"Notification progress refresh regressed to {interval}ms (<30s).")
else:
    errors.append("Could not find notification progress refresh interval.")

if errors:
    print("Task Tunnel battery preflight: FAIL")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Task Tunnel battery preflight: PASS")
print("- No explicit wake locks, exact/background schedulers, or battery-optimization exemption requests.")
print("- Background Compose flow collection is lifecycle-aware.")
print("- Accessibility noise is filtered and master OFF unsubscribes from events.")
print("- Notification refresh, YouTube fallback polling, and Room retention work are bounded/coalesced.")
print("- Physical-device batterystats / Energy Profiler validation is still required for measured drain.")
