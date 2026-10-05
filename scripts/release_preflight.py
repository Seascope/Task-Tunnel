#!/usr/bin/env python3
"""Static release-integrity checks for Task Tunnel.

This intentionally avoids Gradle/network access. It is a fast repository preflight, not a
replacement for building/inspecting the final signed AAB or completing physical QA.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = Path(__file__).resolve().parents[1]
FORM_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]+$")


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


def local_property(name: str) -> str | None:
    path = ROOT / "local.properties"
    if not path.exists():
        return None
    for raw_line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == name:
            return value.strip()
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--allow-placeholder-id",
        action="store_true",
        help="Permit com.example.tasktunnel for historical/internal trees only. Never use for final Play upload.",
    )
    parser.add_argument(
        "--allow-missing-feedback-config",
        action="store_true",
        help="Permit a clean source tree with no Formspree form ID. Never use for the actual tester/Play build.",
    )
    args = parser.parse_args()

    failures: list[str] = []
    notes: list[str] = []

    manifest = parse_xml("app/src/main/AndroidManifest.xml")
    permissions = {node.get(ANDROID + "name") for node in manifest.findall("uses-permission")}
    if "android.permission.INTERNET" not in permissions:
        failures.append("Manifest is missing INTERNET required by the explicit in-app feedback flow.")
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
    elif app_id != "com.rubin.tasktunnel":
        notes.append(f"applicationId is {app_id!r}; confirm this is the permanent Play identity you intend to publish.")

    if 'buildConfigField("String", "FEEDBACK_FORM_ID"' not in gradle:
        failures.append("App module does not expose the configured feedback form ID through BuildConfig.")

    dependency_red_flags = ("firebase", "crashlytics", "sentry", "okhttp", "retrofit", "ktor-client")
    gradle_lower = gradle.lower()
    for token in dependency_red_flags:
        if token in gradle_lower:
            failures.append(f"App module contains unexpected network/telemetry dependency marker: {token}.")

    reporter = read("app/src/main/java/com/example/tasktunnel/feedback/FeedbackReporter.kt")
    if 'https://formspree.io/f/$formId' not in reporter:
        failures.append("FeedbackReporter is missing the expected HTTPS Formspree endpoint.")
    if "HttpURLConnection" not in reporter:
        failures.append("FeedbackReporter no longer contains the explicit direct-submit client.")
    required_feedback_fields = {
        'put("category"',
        'put("message"',
        'put("source"',
        'put("app_version"',
        'put("android_version"',
        'put("accessibility"',
        'put("task_tunnel"',
        'put("drift"',
        'put("notification_controls"',
    }
    for field_marker in required_feedback_fields:
        if field_marker not in reporter:
            failures.append(f"FeedbackReporter is missing expected bounded field marker: {field_marker}.")
    forbidden_feedback_markers = (
        'activity_history', 'attention_history', 'drift_path', 'accessibility_text',
        'screenshot', 'username', 'search_query', 'message_contents', 'raw_tree',
    )
    reporter_lower = reporter.lower()
    # The class comment intentionally names some forbidden categories. Only flag actual put(...) keys.
    for marker in forbidden_feedback_markers:
        if re.search(rf'put\(\s*"{re.escape(marker)}"', reporter_lower):
            failures.append(f"FeedbackReporter unexpectedly includes sensitive payload key: {marker}.")

    feedback_ui = read("app/src/main/java/com/example/tasktunnel/ui/FeedbackScreen.kt")
    if "Include app status" not in feedback_ui or "Send feedback" not in feedback_ui:
        failures.append("Tester feedback UI is missing its explicit send/status controls.")
    settings_ui = read("app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt")
    if 'title = "Report a bug"' not in settings_ui:
        failures.append("Settings does not expose the tester Report a bug entry point.")

    configured_form_id = (
        os.environ.get("TASK_TUNNEL_FEEDBACK_FORM_ID")
        or local_property("TASK_TUNNEL_FEEDBACK_FORM_ID")
        or ""
    ).strip()
    if not configured_form_id:
        message = "TASK_TUNNEL_FEEDBACK_FORM_ID is not configured; tester reports cannot be delivered."
        if args.allow_missing_feedback_config:
            notes.append(message + " Allowed only for this source-only audit.")
        else:
            failures.append(message)
    elif not FORM_ID_PATTERN.fullmatch(configured_form_id):
        failures.append("TASK_TUNNEL_FEEDBACK_FORM_ID is malformed; expected only letters, digits, '_' or '-'.")

    production_files = [
        path for path in (ROOT / "app" / "src" / "main").rglob("*")
        if path.is_file() and path.suffix in {".kt", ".kts", ".xml"}
    ]
    formspree_sources = [
        path.relative_to(ROOT).as_posix()
        for path in production_files
        if "formspree" in path.read_text(encoding="utf-8", errors="ignore").lower()
    ]
    expected_formspree_file = "app/src/main/java/com/example/tasktunnel/feedback/FeedbackReporter.kt"
    unexpected_formspree = [p for p in formspree_sources if p != expected_formspree_file]
    if expected_formspree_file not in formspree_sources:
        failures.append("Expected Formspree reference is missing from FeedbackReporter.")
    if unexpected_formspree:
        failures.append("Unexpected production Formspree references: " + ", ".join(unexpected_formspree))

    review_script = read("PLAY_REVIEW_VIDEO_SCRIPT.md")
    if "Agree & open settings" not in review_script:
        failures.append("Play review video script is not aligned with the current affirmative onboarding action.")
    if "Instagram, YouTube, and TikTok" not in review_script:
        failures.append("Play review video script does not describe all three supported Task Tunnel apps.")
    if "Report a bug" not in review_script:
        failures.append("Play review video script does not disclose the explicit feedback flow.")

    privacy = read("PRIVACY_SUMMARY.md")
    if "Formspree" not in privacy or "Include app status" not in privacy:
        failures.append("Privacy summary is not aligned with the explicit feedback network path.")

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
    print("- Still build/inspect the exact signed AAB and complete physical RC testing before upload.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
