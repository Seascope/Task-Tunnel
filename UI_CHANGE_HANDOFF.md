# Task Tunnel — UI Implementation Handoff & Visual Audit

> **Final verification update:** The two `PaddingValues` compilation errors and secondary-screen Android Back handling identified during the initial audit are fixed. The complete JVM suite (107 tests), connected Android 16 instrumentation suite (2 tests), debug build, and unsigned release build pass. Seven current device captures are in `artifacts/frontend-redesign`; Protection-active and the four Accessibility runtime overlay captures still require the developer to enable Accessibility access manually before capture.

> **Pass 4 update:** The primary header is now compact and inset-aware, navigation uses a quiet selected state, and production icons use Compose Material outlined vectors through the minimal `material-icons-extended` dependency. Attention rows are outcome-led, Episode Detail suppresses adjacent repeated visible times and labels event semantics, and Protection/disclosure actions use moderate-radius geometry. Verification for this pass is pending.

> **Pass 5 update:** Production UI copy now uses plain product language for Accessibility access, local processing, and Attention history. Protection retains its off-state explanation and uses a compact 48dp **Turn protection on** repair action; disclosure content retains every required privacy fact. Build and device-capture verification are pending.

> **Runtime overlay polish pass 2:** Purpose Gate remains the canonical runtime-overlay treatment. Intervention, Drift, and session expiry now reuse its sheet geometry and quiet entrance language; live behavior, priority, timing, callbacks, and history recording are unchanged. The observed **host-app modal stacking edge case** (Task Tunnel over Instagram’s “You’re leaving our app” dialog) is documented only and remains unchanged.

**Date**: 2026-09-21  
**Target Baseline**: Task Tunnel MVP M6 (`4d93e62`)  
**Design Reference**: `Task_Tunnel_UX_Product_Design_Handoff.pdf`  
**Current State Reference**: [CURRENT_STATE.md](file:///f:/coding/android-saas/CURRENT_STATE.md)  
**Status**: Uncommitted working tree UI pass; static visual capture complete; physical-device verification pending.

---

## 1. What changed

Following completion of the M0–M6 feature sequence (where all core logic, Room persistence, detector rules, policies, and Play Store preparation files were implemented), a frontend and UI redesign pass was conducted to elevate Task Tunnel from functional scaffolding into a cohesive, production-grade Android utility.

Key changes across this pass:
1. **Architectural Decomposition**:
   - Decomposed the monolithic ~900-line [MainActivity.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/MainActivity.kt) into dedicated, single-responsibility files:
     - [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt) (Production Compose screens: Attention, Protection, Episode Detail, Settings, Diagnostics, Onboarding, Disclosure)
     - [DeveloperScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/DeveloperScreens.kt) (Debug-only diagnostics, tree inspector, and visual QA controls)
     - [TaskTunnelComponents.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelComponents.kt) (Reusable UI design-system primitives)
     - [TaskTunnelScaffold.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScaffold.kt) (Two-destination primary shell and secondary sub-page scaffolds)
     - [TaskTunnelIcons.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelIcons.kt) (Consistent Compose Material outlined production icons)
     - [Tokens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Tokens.kt) (Shared layout, gap, icon, and touch-target dimensions)
   - [MainActivity.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/MainActivity.kt) was reduced to 229 lines, acting strictly as a navigation router, lifecycle watcher, and state coordinator.

2. **Design System & Visual Language**:
   - Replaced default Material 3 purple/teal styling with a unified dark palette: neutral charcoal canvas (`#101214`), graphite cards/sheets (`#191C1F`), soft brand blue (`#79AFFF`), muted secondary text (`#A7ADB4`), and restrained semantic health indicators (Healthy green `#75B995`, Limited amber `#D6A85F`, Error red `#E28B88`).
   - Replaced Material 3 "card soup" (heavy nested bordered cards) with clean utility-style list rows, inset dividers, and clear typographic hierarchy.
   - Replaced default typography with explicit `AndroidSans` (system sans-serif) scales spanning display, headlines, titles, body, and labels.

3. **Overlay Transformation**:
   - Overhauled the legacy top-anchored floating dialog boxes in [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt) into modern bottom sheets anchored to `Gravity.BOTTOM` with 28dp top rounded corners, centered grab handles, 0.42f background scrim dimming, slide-up translation/fade animations, system navigation bar inset awareness, and distinct visual button hierarchies.

4. **Visual QA Tooling**:
   - Introduced `VisualQaOverlay` in [AccessibilityRuntime.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt) and developer actions in [DeveloperScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/DeveloperScreens.kt), allowing on-demand rendering of Purpose Gate, Intervention, Drift check-in, and Session Expiry overlays on device without triggering live service conditions.

---

## 2. Screens changed

### Onboarding (5 steps)
- **Files**: [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt#L377-L519)
- **Major layout changes**: 5-step guided flow with persistent top tracking header (`TASK TUNNEL · Step X of 5`), vertical scrollable content body, and bottom action bar containing a primary filled button (52dp) and a text button (`Set up later`). Steps:
  1. *Value*: Numbered points ("01", "02", "03") explaining the calm pause mental model.
  2. *How it works*: Reassurance that it is a quiet layer; explains fail-open and intentional browsing.
  3. *Disclosure*: Comprehensive privacy and AccessibilityService disclosure before permission request.
  4. *Verify*: Live check of accessibility access state with button to settings or continue without access.
  5. *Configure*: Protection summary for Instagram & YouTube with optional Drift toggles.
- **Visual changes**: Numbered indicators in Brand Blue, bold titles, muted secondary body text. Row layout places headings in weighted columns with copy underneath to prevent narrow vertical text wrapping.
- **Interaction changes**: Affirmative action button advances or directs directly to Android Accessibility settings. User can defer at any point with `Set up later`.
- **Anything still temporary**: Verify screen requires manual return from OS settings; auto-advancing on resume from settings is handled by lifecycle refresh.

### Attention (Main Destination 1)
- **Files**: [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt#L80-L150)
- **Major layout changes**: Chronological feed leading with quiet `ProtectionStatusLine` at top, contextual `ActiveTunnelNotice` when a tunnel is running, "Today" section, "Earlier" section, and a quiet "This week" summary.
- **Visual changes**: Cards are eliminated in favor of clean list rows with app identity, intent, quiet time metadata, and a meaningful outcome only when the episode includes an intervention or decision. Drift uses a text-first treatment rather than overlapping app icons.
- **Interaction changes**: Tapping an episode row opens its full causal timeline in `EpisodeDetailScreen`.
- **Anything still temporary**: Pass 4 verification is pending.

### Episode Detail
- **Files**: [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt#L178-L250)
- **Major layout changes**: Header displaying timestamp range, natural-language episode title, subtitle, and involved app icons (for Drift), followed by the `EpisodePath` timeline.
- **Visual changes**: Replaced raw event tables with `EpisodePathNode`, a custom vertical tree node drawn on Canvas featuring continuous vertical stem lines, hollow/solid node rings (Brand Blue for Intent, muted for transitions), timestamps on the left, and human-readable event copy on the right.
- **Interaction changes**: Read-only reflective view. Reached by tapping any episode in Attention.
- **Anything still temporary**: Pass 4 verification is pending.

### Protection (Main Destination 2)
- **Files**: [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt#L253-L340)
- **Major layout changes**: Follows the strict 3-tier IA:
  1. *Is it working?*: Top health status dot and summary text, with immediate "Enable Accessibility access" repair button and OEM background advice when impaired.
  2. *What is protected?*: Intention-centric rows for Instagram and YouTube displaying configured intentions and compatibility state.
  3. *How is it configured?*: Drift Detection master switch and per-app toggles, followed by system diagnostic rows.
- **Visual changes**: High-contrast health status dots (Green, Amber, Red). App icons pulled dynamically from Android `PackageManager` with fallback colored badges.
- **Interaction changes**: Tapping Protection Health or Accessibility Access navigates to sanitized Diagnostics or system Settings.
- **Anything still temporary**: Pass 4 verification is pending.

### Settings
- **Files**: [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt#L564-L636)
- **Major layout changes**: Clean grouped sections:
  - *Privacy & data*: Privacy & Accessibility disclosure, Clear Attention history (with `AlertDialog` confirmation).
  - *Help & system*: Protection diagnostics (sanitized report), Developer options (gated by `BuildConfig.DEBUG`).
  - *About*: App version, local-first architecture reassurance.
- **Visual changes**: Uniform `SettingsRow` layout with 42dp leading icon containers, chevron navigation indicators, and inset dividers.
- **Interaction changes**: Red destructive confirmation dialog for database clearing; immediate in-memory state feedback.
- **Anything still temporary**: None.

### Diagnostics
- **Files**: [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt#L639-L670)
- **Major layout changes**: Monospaced-style plain-text report listing sanitized environment state (OS, SDK, service status, app package versions, Drift pool, database status) with bottom "Copy diagnostics" button.
- **Visual changes**: Clean vertical list with explicit privacy note explaining that no user content or private events are included.
- **Interaction changes**: Copies plain text directly to the system clipboard; shows feedback state ("Diagnostics copied").
- **Anything still temporary**: None.

### Purpose Gate (Runtime Overlay)
- **Files**: [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt#L597-L647)
- **Major layout changes**: Bottom sheet (`baseTunnelOverlay`) with grab handle, app identity header, "What are you here to do?" title, 58dp choice rows with right chevrons, expandable time limit selector, and a "Not now" dismissal button.
- **Visual changes**: Sheet container `#191C1F`, 28dp top rounded corners, 0.42f window dimming.
- **Interaction changes**: Haptic tap feedback (`KEYBOARD_TAP`) on selecting a task. Expands an in-place `RadioGroup` when tapping "Add a time limit".
- **Anything still temporary**: The time limit uses standard Android `RadioButton` views inside a horizontal `RadioGroup`.

### Task Tunnel Intervention (Runtime Overlay)
- **Files**: [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt#L649-L698)
- **Major layout changes**: Bottom sheet displaying the app header, clear statement that the surface is outside the Tunnel, the original intention reminder, a primary filled button to return, and secondary text buttons for detour or exit.
- **Visual changes**: Calm, non-punitive aesthetic. No alarm-red styling. Primary button filled in Brand Blue (`#79AFFF`).
- **Interaction changes**: "Return to [messages/search]" triggers `GLOBAL_ACTION_BACK` with cooldown; "Allow [surface] for now" grants a 5-minute scoped allowance; "End Tunnel" terminates session without closing app.
- **Anything still temporary**: Button text is "Allow [surface] for now" rather than generic "Allow Anyway".

### Session-Expiry Intervention (Runtime Overlay)
- **Files**: [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt#L700-L746)
- **Major layout changes**: Bottom sheet: "Your chosen time is complete", "What would you like to do next?", primary "Continue" / "Keep browsing" action, secondary "Finish", and quiet "Choose another purpose".
- **Visual changes**: Neutral, non-shaming presentation. No "Time's Up!" or punitive warning graphics.
- **Interaction changes**: Finish clears session cleanly; Continue extends by the original duration window; Choose another purpose reopens the Purpose Gate.
- **Anything still temporary**: None.

### Drift Check-in (Runtime Overlay)
- **Files**: [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt#L559-L588)
- **Major layout changes**: Bottom sheet featuring a compact, arrow-connected sequence of involved app icons, observational heading "Looking for something?", conversational description ("You moved between Instagram, Reddit and YouTube in under a minute."), primary "Set an intention" button, and secondary "Keep going" text button.
- **Visual changes**: Light scrim (`0.32f`), softer than surface interventions.
- **Interaction changes**: "Set an intention" routes to Purpose Gate if on Instagram/YouTube; "Keep going" acknowledges without shaming.
- **Anything still temporary**: None.

### Developer Options & Inspector (Debug Only)
- **Files**: [DeveloperScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/DeveloperScreens.kt)
- **Major layout changes**: Single-scroll lists for runtime inspection, fingerprint extraction, and visual QA overlay testing.
- **Visual changes**: Raw engineering diagnostic appearance; retains standard `Card` containers.
- **Interaction changes**: Triggers visual QA overlays on demand; copies sanitized fingerprints.
- **Anything still temporary**: Deliberately utilitarian and excluded from release builds.

---

## 3. Design system

| Token Category | Implemented Specification | Centralized vs Hardcoded |
| :--- | :--- | :--- |
| **Canvas & Surfaces** | `Canvas` (`#101214`), `SurfaceGraphite` (`#191C1F`), `SurfaceRaised` (`#202428`) | Centralized in [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt). *Note: Duplicated as private `COLOR_SHEET` hex constant in [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt#L947).* |
| **Text Colors** | `TextPrimary` (`#F1F3F4`), `TextSecondary` (`#A7ADB4`) | Centralized in [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt); duplicated in Service. |
| **Dividers & Outlines**| `Divider` (`#2A2E32`), `COLOR_HANDLE` (`#5E646B`) | Centralized in [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt); duplicated in Service. |
| **Action & Accents** | `BrandBlue` (`#79AFFF`), `BrandBlueContainer` (`#14345F`), `onPrimary` (`#061A32`) | Centralized in [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt); duplicated in Service. |
| **Semantic Health** | `HealthyGreen` (`#75B995`), `LimitedAmber` (`#D6A85F`), `ErrorRed` (`#E28B88`) | Centralized in [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt). |
| **App Identity Fallbacks**| Instagram (`#D65A79`), YouTube (`#E45B55`), Reddit (`#FF6B35`) | Centralized in [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt); duplicated in Service. |
| **Typography** | `AndroidSans` (system SansSerif). DisplaySmall (36sp), HeadlineLarge (30sp), HeadlineMedium (25sp), HeadlineSmall (22sp), TitleLarge (20sp), TitleMedium (17sp), TitleSmall (14sp), BodyLarge (16sp), BodyMedium (14sp), BodySmall (12sp), LabelLarge (14sp), LabelMedium (12sp), LabelSmall (11sp). | Centralized in [Type.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Type.kt). Overlays use native `TextView` sizes (23sp, 17sp, 15sp, 14sp, 13sp). |
| **Corner Radii** | ExtraSmall (6dp), Small (10dp), Medium (14dp), Large (20dp), ExtraLarge (Top 28dp for bottom sheets). | Centralized in [Theme.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Theme.kt); overlays use explicit 28dp corner array. |
| **Spacing Conventions**| ScreenPadding (18dp), MajorSectionGap (28dp), SectionHeaderBottomGap (8dp), RowVerticalPadding (14dp), IconTextGap (13dp), SecondaryTextGap (4dp), TouchTarget (48dp). | Centralized in [Tokens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Tokens.kt). |
| **Button Styles** | Primary: Filled 52dp button, Brand Blue background, onPrimary dark text, 10dp rounded corners. Text/Ghost: 48dp minimum touch target, Brand Blue tint, system ripple background. | Implemented via Compose `Button` / `TextButton` and Service `overlayPrimaryButton` / `overlayTextButton`. |
| **Navigation Style** | Two-destination bottom `NavigationBar` (Attention / Protection) with 0dp tonal elevation; secondary sub-pages use `SecondaryScaffold` with back navigation arrow. | Centralized in [TaskTunnelScaffold.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScaffold.kt). |
| **Icon Approach** | Compose Material outlined icon family via [TaskTunnelIcons.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelIcons.kt). | Centralized in [TaskTunnelIcons.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelIcons.kt). |
| **Elevation / Borders**| No floating drop-shadows on screens (flat dark canvas). Overlays use 12dp elevation + 0.42f window scrim. Dividers are 1dp `#2A2E32`. | Tokens centralized; overlay elevation hardcoded in service. |
| **Animation / Motion** | Compact native `TopAppBar`; overlay entry uses 160ms slide-up translation (`32dp -> 0dp`) and fade (`0 -> 1`). | Overlays animated via `ViewPropertyAnimator` in service. |

---

## 4. Reusable UI components

The following reusable components are defined in [TaskTunnelComponents.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelComponents.kt), [TaskTunnelScaffold.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScaffold.kt), and [TaskTunnelIcons.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelIcons.kt):

1. **`SectionHeader`**:
   - Small muted category label (`titleSmall`, `#A7ADB4`) with accessibility heading semantics.
   - *Used in*: AttentionScreen, ProtectionScreen, SettingsScreen.
2. **`ProtectionStatusLine`**:
   - Compact health status row displaying an 8dp colored circular canvas dot + status title (`titleMedium`) and optional summary copy.
   - *Used in*: AttentionScreen (top status summary), ProtectionScreen (hero health status).
3. **`AppIcon` & `AppIdentity`**:
   - Resolves native application icons directly from `PackageManager` as bitmaps; falls back to brand-tinted rounded boxes with first-letter monograms.
   - *Used in*: Attention episode rows, Protection app rows, ActiveTunnelNotice, Onboarding, Drift app selections.
4. **`SettingsRow`**:
   - Standardized 48dp+ interactive row supporting optional leading icon/graphic, title, 2-line subtitle, trailing text badge, and navigation chevron.
   - *Used in*: ProtectionScreen (Protection health, Accessibility access, Protected apps), SettingsScreen (all rows), Onboarding.
5. **`RowDivider`**:
   - 1dp `#2A2E32` horizontal rule supporting optional 55dp inset to align with text margins past leading icons.
   - *Used in*: Attention episode list, Protection app list, Settings sections.
6. **`EpisodeRow` & `DriftEpisodeRow`**:
   - List rows for attention episodes featuring app identity, quiet time metadata, declared purpose, and outcome-led summaries when an intervention or decision occurred.
   - *Used in*: AttentionScreen episode list.
7. **`IndentedEventList`**:
   - Episode summaries present a meaningful outcome only when the episode includes an intervention or decision.
   - *Used in*: `EpisodeRow` and `DriftEpisodeRow`.
8. **Drift row treatment**:
   - A compact text-first row with a subtle Attention icon replaces overlapping app-icon clusters.
   - *Used in*: `DriftEpisodeRow`.
9. **`EmptyState` & `ErrorNotice`**:
   - Standardized quiet empty container and graphite-backed error notice box with red heading.
   - *Used in*: AttentionScreen (no history, empty day, unavailable database), EpisodeDetailScreen.
10. **`TaskTunnelScaffold` & `SecondaryScaffold`**:
    - Primary shell with compact `TopAppBar` and two-destination `NavigationBar`; secondary shell with flat `TopAppBar` and back arrow.
    - *Used in*: Main application shell and sub-screens.
11. **`TaskTunnelIcon`**:
    - Compose Material outlined icon mapping for ATTENTION, PROTECTION, SETTINGS, BACK, CHEVRON, DELETE, COPY, INFO, MESSAGE, BROWSE, and TIMER.
    - *Used in*: Top bars, bottom navigation bar, settings rows, and action headers.

---

## 5. Navigation / IA changes

The application adheres strictly to the approved Information Architecture:

```mermaid
flowchart TD
    Shell[TaskTunnelScaffold] --> Attention[Attention Screen (Default Destination)]
    Shell --> Protection[Protection Screen]
    Shell -->|TopAppBar Gear Icon| Settings[Settings Screen]
    
    Attention -->|Tap Episode| EpisodeDetail[Episode Detail Screen]
    Protection -->|Tap Health / Accessibility| Diagnostics[Protection Diagnostics]
    Protection -->|Repair Accessibility| Disclosure[Privacy & Accessibility Disclosure]
    Settings -->|Privacy & Accessibility| Disclosure
    Settings -->|Diagnostics| Diagnostics
    Settings -->|Debug Only| Developer[Developer Options]
    Developer --> Inspector[Sanitized Inspector]
```

### Destination Specifics:
- **Attention**: Default primary destination answering *"What happened to my attention?"*. Contains chronological episodes, active tunnel banner, and weekly drift count.
- **Protection**: Second primary destination answering *"Is it working? What is protected? How is it configured?"*. Contains health status, Instagram/YouTube intention policies, and Drift pool switches.
- **Settings**: Secondary utility destination reached via the top-right gear icon in the compact primary top bar.
- **Nested Screens**:
  - `EpisodeDetailScreen` (replaces Attention)
  - `DiagnosticsScreen` (accessible from Protection and Settings)
  - `AccessibilityDisclosureScreen` (accessible from Protection repair and Settings)
  - `DeveloperScreen` & `InspectorScreen` (accessible from Settings in debug builds only)
- **Back Navigation Handling**:
  - Back navigation from all nested screens is visually facilitated by a top-left `TaskTunnelIconKind.BACK` icon in `SecondaryScaffold`.
  - **CRITICAL DEFECT IDENTIFIED**: Android system hardware back / gesture navigation is currently **not intercepted** with `BackHandler` in Compose. Pressing the device back gesture while inside Episode Detail, Settings, or Diagnostics immediately exits the application to the Android launcher instead of popping back to the previous screen.

---

## 6. Runtime overlay changes

All runtime overlays in [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt) were completely rebuilt from raw Android floating boxes into uniform, polished bottom sheets:

```
┌──────────────────────────────────────────────┐
│                  ━━━━ handle ━━━━            │
│  [Icon] App Name                             │
│  Heading Title (23sp bold)                   │
│  Secondary description / reminder (15sp)     │
│                                              │
│  [ Choice Row / Primary Filled Button ]      │
│  ------------------------------------------  │
│  [ Choice Row / Secondary Text Button ]      │
│  [ Tertiary / Not Now Text Button ]          │
└──────────────────────────────────────────────┘
```

1. **Purpose Gate**:
   - *Appearance*: Anchored bottom sheet on top of Instagram or YouTube. Top drag handle, app icon + label, bold header `"What are you here to do?"`. Two 58dp choice rows with right chevrons (`›`) and an inset divider. Below choices: `"Add a time limit"` toggle and centered `"Not now"` text button.
   - *Behavior*: Tapping a task fires haptic feedback and immediately starts the session. Tapping `"Add a time limit"` expands a horizontal radio group with choices (`No limit`, `5 min`, `10 min`, `20 min`). Tapping `"Not now"` cleanly dismisses the gate.
2. **Incompatible-Surface Intervention**:
   - *Appearance*: Bottom sheet with app header, heading `"[Reels/Shorts/Explore] isn't part of this Tunnel"`, and subtitle `"You came here to [reply to messages / search]"`.
   - *Actions*:
     - Primary button: Filled Brand Blue `"Return to [messages / search / video]"`.
     - Secondary button: Text button `"Allow [surface] for now"`.
     - Tertiary button: Text button `"End Tunnel"`.
   - *Behavior*: "Return" applies a 2-second anti-loop cooldown and executes `GLOBAL_ACTION_BACK`. "Allow for now" sets a 5-minute scoped allowance for that specific surface. "End Tunnel" cancels the session without closing the host app.
3. **Allow Anyway**:
   - *Appearance*: Presented as the secondary text button inside the surface intervention.
   - *Behavior*: Tapping grants immediate access to the detected surface for 5 minutes. Does not pop up unnecessary duration confirmation forms; preserves the broader active Tunnel.
4. **Expiry Decision**:
   - *Appearance*: Bottom sheet appearing upon foreground expiration: `"Your chosen time is complete"`, subtitle `"What would you like to do next?"`.
   - *Actions*:
     - Primary button: `"Finish"`.
     - Secondary button: `"Continue"` (or `"Keep browsing"` if browsing intentionally).
     - Tertiary button: `"Choose another purpose"`.
   - *Behavior*: "Finish" ends the Tunnel; "Continue" resets the timer for another equal window; "Choose another purpose" returns to the Purpose Gate.
5. **Drift Check-in**:
   - *Appearance*: Softer bottom sheet (dim scrim `0.32f`), sequence of involved app icons, heading `"Looking for something?"`, observational copy `"You moved between [App A], [App B] and [App C] in under a minute."`.
   - *Actions*: Primary filled button `"Set an intention"`, secondary text button `"Keep going"`.
   - *Behavior*: One check-in per episode. "Set an intention" redirects to Purpose Gate if on Instagram or YouTube; "Keep going" dismisses and logs the choice without penalty or shaming.

---

## 7. Design-source deviations

Comparison against `Task_Tunnel_UX_Product_Design_Handoff.pdf`:

| Feature / Pattern | Design Handoff Specification | Current Implementation | Deviation Classification | Rationale / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Attention Screen Timeline vs Episodes** | PDF page 6 depicts a chronological timeline table (Time, Event). Section 15 recommends hero episodes + dedicated detail screen. | Grouped episodes on Attention screen (`EpisodeRow`); full timeline node tree in `EpisodeDetailScreen`. | **Intentional improvement** | Directly implements Section 15 recommendation. Avoids cluttering the main screen while making causal sequences clear. |
| **Intervention Copy** | *"You opened Instagram to reply. Reels is outside this Task Tunnel."* | Heading: *"Reels isn't part of this Tunnel"*; Subtitle: *"You came here to reply to messages."* | **Intentional improvement** | Provides immediate, scannable visual hierarchy on mobile screens. |
| **Intervention Action Label** | Canonical action list: *Return • End Tunnel • Allow Anyway* | Action button reads *"Allow [surface] for now"* | **Intentional improvement** | Explicitly communicates that the allowance is temporary and scoped to the surface, reducing user confusion. |
| **Purpose Gate Time Selector** | Optional lightweight time choices (No limit, 5 min, 10 min, 20 min). | Toggle text button `"Add a time limit"` expanding an Android `RadioGroup` with horizontal `RadioButton`s. | **Temporary implementation** | Native radio buttons inside an accessibility overlay look utilitarian and unstyled. Should eventually be replaced with custom pill chips. |
| **System Back Navigation** | Standard Android navigation rules (back button returns to previous screen). | Android back button exits the app; only the top-left on-screen back icon pops destinations. | **Accidental / needs correction** | Missing `BackHandler` in Compose in [MainActivity.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/MainActivity.kt). |
| **PaddingValues Compilation Error** | Functional compilation of screens. | `TaskTunnelScreens.kt` lines 91 and 264 use `PaddingValues(horizontal = ..., top = ..., bottom = ...)` which does not exist in Compose API. | **Accidental / needs correction** | Fails `compileDebugKotlin`. Must be replaced with `PaddingValues(start = ..., top = ..., end = ..., bottom = ...)`. |
| **Duplicate Overlay Constants** | Cohesive design system across app and overlays. | `COLOR_*` and dimensions are defined as private constants in `TaskTunnelAccessibilityService.kt`. | **Temporary implementation** | Accessibility overlays use raw Android `View`s to avoid Compose runtime overhead in the service process; requires manual synchronization of color hex values. |

---

## 8. Known visual problems

A rigorous and critical review identified the following defects:

1. **Compilation Breakage (CRITICAL)**:
   - In [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt) at lines 91 and 264, `LazyColumn` uses:
     ```kotlin
     contentPadding = PaddingValues(
         horizontal = TaskTunnelTokens.ScreenHorizontalPadding,
         top = 8.dp,
         bottom = 32.dp,
     )
     ```
     The Compose `PaddingValues` overload does not accept `(horizontal, top, bottom)`. This causes `:app:compileDebugKotlin` to fail immediately.
2. **Missing System Back Handler (CRITICAL UX BUG)**:
   - When navigated into `EpisodeDetailScreen`, `SettingsScreen`, `DiagnosticsScreen`, or `AccessibilityDisclosureScreen`, pressing the Android gesture/hardware back button immediately exits to the phone's home screen. Only tapping the on-screen top-bar back arrow returns to the parent screen. `BackHandler` must be integrated into `MainActivity.kt`.
3. **Utilitarian Radio Buttons in Purpose Gate**:
   - The time limit selector inside `purposeGateView()` uses standard platform `RadioButton` views inside a horizontal `RadioGroup`. They appear with default unstyled radio circles and look out of place against the custom dark bottom sheet styling.
4. **Developer Options & Inspector Card Soup**:
   - [DeveloperScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/DeveloperScreens.kt) still uses heavy Material 3 default `Card` containers, unstyled buttons, and raw text dumps. While debug-only, it contrasts sharply with the refined design system of the production screens.
5. **Missing Tap Affordance on Attention Episodes**:
   - `EpisodeRow` and `DriftEpisodeRow` have full-row clickability to open the causal timeline, but feature no chevron (`›`) or visual indicator that they are interactive.
6. **Production icon consistency**:
   - Resolved in Pass 4 by replacing hand-drawn Canvas icons with the Compose Material outlined icon family.
7. **Plain Text Empty States**:
   - `EmptyState` renders only plain title and body text without any subtle vector icon, making the empty Attention screen feel slightly barren.
8. **Double Theme Definition**:
   - Color values (`#101214`, `#191C1F`, `#79AFFF`, etc.) exist in both [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt) and as private constants in [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt). Any future color palette adjustment requires editing both files.

---

## 9. Files changed

### Modified Files:
- [MainActivity.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/MainActivity.kt)
- [AccessibilityRuntime.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt)
- [TaskTunnelAccessibilityService.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt)
- [Color.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt)
- [Theme.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Theme.kt)
- [Type.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Type.kt)
- [colors.xml](file:///f:/coding/android-saas/app/src/main/res/values/colors.xml)
- [themes.xml](file:///f:/coding/android-saas/app/src/main/res/values/themes.xml)
- [AGENTS.md](file:///f:/coding/android-saas/AGENTS.md)
- [CURRENT_STATE.md](file:///f:/coding/android-saas/CURRENT_STATE.md)

### New Untracked Files:
- [DeveloperScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/DeveloperScreens.kt)
- [TaskTunnelComponents.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelComponents.kt)
- [TaskTunnelIcons.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelIcons.kt)
- [TaskTunnelScaffold.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScaffold.kt)
- [TaskTunnelScreens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt)
- [Tokens.kt](file:///f:/coding/android-saas/app/src/main/java/com/example/tasktunnel/ui/theme/Tokens.kt)
- Visual Artifacts: `artifacts/frontend-redesign/*.png`

---

## 10. Current screenshots needed

To enable a designer or external reviewer to judge the current implementation on a physical device, the following captures are required:

1. **Onboarding Steps**:
   - `onboarding_01_value.png`: Step 1 Value proposition.
   - `onboarding_02_how_it_works.png`: Step 2 Quiet layer explanation.
   - `onboarding_03_disclosure.png`: Step 3 Accessibility disclosure.
   - `onboarding_04_verify_off.png`: Step 4 Verify when accessibility is disabled.
   - `onboarding_05_configure.png`: Step 5 Intention and Drift setup.
2. **Attention & Reflection**:
   - `attention_empty.png`: Attention screen when no history has been recorded.
   - `attention_populated.png`: Attention screen with populated Task Tunnel and Drift episodes.
   - `attention_active_tunnel.png`: Attention screen showing the top `ActiveTunnelNotice` while a tunnel is active.
   - `episode_detail_tunnel.png`: Episode detail timeline node tree for a Task Tunnel episode.
   - `episode_detail_drift.png`: Episode detail timeline node tree for a multi-app Drift episode.
3. **Protection**:
   - `protection_active.png`: Protection screen in normal active state (green status dot).
   - `protection_off.png`: Protection screen with accessibility disabled (showing red indicator and repair action).
4. **Settings & Diagnostics**:
   - `settings_main.png`: Settings screen showing sections and options.
   - `settings_clear_dialog.png`: Confirmation dialog for clearing attention history.
   - `diagnostics_report.png`: Sanitized technical status report.
5. **Runtime Overlays** *(can be captured using the Visual QA triggers in Developer Options)*:
   - `overlay_purpose_gate.png`: Purpose Gate bottom sheet over Instagram or YouTube.
   - `overlay_purpose_gate_timer.png`: Purpose Gate with "Add a time limit" expanded.
   - `overlay_surface_intervention.png`: Surface intervention over Reels or Shorts.
   - `overlay_expiry_decision.png`: Session expiry bottom sheet.
   - `overlay_drift_checkin.png`: Drift check-in bottom sheet over distraction app.

---

## 11. Git summary

### Git Diff Stat (Modified files in working tree)
```text
 AGENTS.md                                          | 538 ++++++++++++++---
 CURRENT_STATE.md                                   |  10 +
 app/src/main/java/com/example/tasktunnel/MainActivity.kt    | 651 ++++-----------------
 app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt          |   9 +
 app/src/main/java/com/example/tasktunnel/accessibility/TaskTunnelAccessibilityService.kt | 292 +++++++--
 app/src/main/java/com/example/tasktunnel/ui/theme/Color.kt  |  21 +-
 app/src/main/java/com/example/tasktunnel/ui/theme/Theme.kt  |  76 ++-
 app/src/main/java/com/example/tasktunnel/ui/theme/Type.kt   |  43 +-
 app/src/main/res/values/colors.xml                 |  11 +-
 app/src/main/res/values/themes.xml                 |  14 +-
 10 files changed, 887 insertions(+), 778 deletions(-)
```

### Untracked UI Files
```text
app/src/main/java/com/example/tasktunnel/ui/DeveloperScreens.kt   | 167 lines
app/src/main/java/com/example/tasktunnel/ui/TaskTunnelComponents.kt | 277 lines
app/src/main/java/com/example/tasktunnel/ui/TaskTunnelIcons.kt      | 111 lines
app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScaffold.kt   | 116 lines
app/src/main/java/com/example/tasktunnel/ui/TaskTunnelScreens.kt    | 678 lines
app/src/main/java/com/example/tasktunnel/ui/theme/Tokens.kt         |  17 lines
artifacts/frontend-redesign/*.png                                   | 7 image captures
```

### Current Commit Hash
- **Commit**: `4d93e62dbe3f10175630813e5d39e901abb8579a` (`milestone: complete Task Tunnel MVP M0-M6`)

### Status of UI Changes
- **Uncommitted Changes**: Yes. All UI redesign changes and newly introduced UI module files are currently uncommitted in the working tree.
- **Build Status**: The current working tree does **not** compile due to two `PaddingValues` argument mismatches in `TaskTunnelScreens.kt` (lines 91 and 264). Implementation changes were strictly avoided per instructions.
