#!/usr/bin/env python3
"""Static release-integrity checks for Task Tunnel.

This intentionally avoids Gradle/network access. It is a fast repository preflight, not a
replacement for building/inspecting the final signed AAB or completing physical QA.
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def parse_xml(relative: str) -> ET.Element:
    return ET.parse(ROOT / relative).getroot()


def has_exclude(root: ET.Element, parent_tag: str | None, domain: str) -> bool:
    parents = [root] if parent_tag is None else list(root.findall(parent_tag))
    return any(
        node.get("domain") == domain and node.get("path") == "."
        for parent in parents
        for node in parent.findall("exclude")
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--allow-placeholder-id",
        action="store_true",
        help="Permit com.example.tasktunnel for internal/beta preflight. Never use for final Play upload.",
    )
    args = parser.parse_args()

    failures: list[str] = []
    notes: list[str] = []

    manifest = parse_xml("app/src/main/AndroidManifest.xml")
    permissions = {
        node.get(ANDROID + "name")
        for node in manifest.findall("uses-permission")
    }
    if "android.permission.INTERNET" in permissions:
        failures.append("Manifest declares INTERNET; current Play build is expected to be local-only.")
    if "android.permission.QUERY_ALL_PACKAGES" in permissions:
        failures.append("Manifest declares QUERY_ALL_PACKAGES; Drift picker must not require it.")

    application = manifest.find("application")
    if application is None:
        failures.append("Manifest has no <application> element.")
    else:
        receiver = next(
            (n for n in application.findall("receiver") if (n.get(ANDROID + "name") or "").endswith("TunnelNotificationActionReceiver")),
            None,
        )
        if receiver is None or receiver.get(ANDROID + "exported") != "false":
            failures.append("TunnelNotificationActionReceiver must exist and remain android:exported=\"false\".")

        service = next(
            (n for n in application.findall("service") if (n.get(ANDROID + "name") or "").endswith("TaskTunnelAccessibilityService")),
            None,
        )
        if service is None:
            failures.append("TaskTunnelAccessibilityService is missing from the manifest.")
        else:
            if service.get(ANDROID + "exported") != "true":
                failures.append("AccessibilityService must remain exported for Android system discovery.")
            if service.get(ANDROID + "permission") != "android.permission.BIND_ACCESSIBILITY_SERVICE":
                failures.append("AccessibilityService must remain protected by BIND_ACCESSIBILITY_SERVICE.")

        if not application.get(ANDROID + "fullBackupContent"):
            failures.append("Application is missing legacy fullBackupContent rules.")
        if not application.get(ANDROID + "dataExtractionRules"):
            failures.append("Application is missing Android 12+ dataExtractionRules.")

    accessibility = parse_xml("app/src/main/res/xml/accessibility_service_config.xml")
    if accessibility.get(ANDROID + "isAccessibilityTool") != "false":
        failures.append("Accessibility metadata must declare isAccessibilityTool=false for Task Tunnel.")
    if accessibility.get(ANDROID + "canRetrieveWindowContent") != "true":
        failures.append("Accessibility metadata unexpectedly disabled window-content retrieval.")
    if accessibility.get(ANDROID + "canPerformGestures") == "true":
        failures.append("Accessibility metadata unexpectedly requests gesture capability.")
    if accessibility.get(ANDROID + "canRequestFilterKeyEvents") == "true":
        failures.append("Accessibility metadata unexpectedly requests filter-key capability.")

    legacy_backup = parse_xml("app/src/main/res/xml/backup_rules.xml")
    for domain in ("database", "sharedpref"):
        if not has_exclude(legacy_backup, None, domain):
            failures.append(f"Legacy backup rules do not exclude {domain} path '.'.")

    extraction = parse_xml("app/src/main/res/xml/data_extraction_rules.xml")
    for parent in ("cloud-backup", "device-transfer"):
        for domain in ("database", "sharedpref"):
            if not has_exclude(extraction, parent, domain):
                failures.append(f"{parent} rules do not exclude {domain} path '.'.")

    gradle = read("app/build.gradle.kts")
    app_id_match = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
    app_id = app_id_match.group(1) if app_id_match else None
    if not app_id:
        failures.append("Could not determine applicationId from app/build.gradle.kts.")
    elif app_id == "com.example.tasktunnel":
        message = "applicationId is still the placeholder com.example.tasktunnel."
        if args.allow_placeholder_id:
            notes.append(message + " Allowed only because --allow-placeholder-id was supplied.")
        else:
            failures.append(message)

    dependency_red_flags = ("firebase", "crashlytics", "sentry", "okhttp", "retrofit", "ktor-client")
    gradle_lower = gradle.lower()
    for token in dependency_red_flags:
        if token in gradle_lower:
            failures.append(f"App module contains unexpected network/telemetry dependency marker: {token}.")

    production_text = "\n".join(
        path.read_text(encoding="utf-8", errors="ignore")
        for path in (ROOT / "app" / "src" / "main").rglob("*")
        if path.is_file() and path.suffix in {".kt", ".kts", ".xml"}
    ).lower()
    if "formspree" in production_text:
        failures.append("Production source still contains a Formspree reference.")

    review_script = read("PLAY_REVIEW_VIDEO_SCRIPT.md")
    if "Agree & open settings" not in review_script:
        failures.append("Play review video script is not aligned with the current affirmative onboarding action.")
    if "Instagram, YouTube, and TikTok" not in review_script:
        failures.append("Play review video script does not describe all three supported Task Tunnel apps.")

    print("Task Tunnel release preflight")
    print(f"Root: {ROOT}")
    if notes:
        print("\nNOTES")
        for note in notes:
            print(f"- {note}")
    if failures:
        print("\nFAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("\nPASS")
    print("- Static repository checks passed.")
    print("- Still inspect/build the exact signed AAB and complete physical RC testing before upload.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
