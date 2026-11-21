# Mirrolit manual checklist

What the Maestro suite cannot do (real sign-in, Play Billing, two Play builds, paid API keys, simulated failures,
visual judgement) as a fill-in sheet. Full wording of every step, fixtures and case ids:
`docs/e2e/e2e_journeys.md` (journey ids below) and `docs/e2e/e2e_test_cases.md` (case ids in brackets).
Automation and its limits: `docs/e2e/README.md`, `e2e/README.md`.

## How to fill it in

Copy this file per run (e.g. `releases/manual-<versionName>-<versionCode>.md`) or print it. At the top of the
copy, fill the run header; in every journey table tick **P** (pass) or **F** (fail) per checkpoint and write the
case id, a screenshot name or a bug link under Notes. Blank = not run. A fail on a "known bug" row is reported,
never blocking. A "void" (network down, Play Console unavailable) is written in Notes, not ticked P.

| Field | Value |
|---|---|
| Build | `io.mirrolit.app` versionName / versionCode: |
| Installed from | Play internal / closed track, or sideload (state which per journey) |
| Date / tester | |
| Phone | Pixel 7 phone, Android __ |
| Tablet | Xiaomi tablet (MIUI / HyperOS), Android __ |
| Other devices | e.g. tablet with persistent taskbar, tablet AVDs API 26/30/33/35/37 |

Per-journey device fields below name the device class; write the actual OS version in Notes.

## When to run what

| Release kind | Run |
|---|---|
| Every release (hotfix, small fix, versionCode bump) | none (the automated suite and release smoke R-01..R-06 are enough) |
| Larger release (new feature, parser/translator/DB/billing/auth change, new target SDK, R8 or dependency bump) | all **P0** manual journeys (column "When" = L) |
| Closed-testing cycle (start of each 12-tester / 14-day cycle) | all journeys, P0 first, then P1, P2, known-bug probe |

"When": **L** = larger release and every closed-testing cycle; **C** = closed-testing cycle only.

## Index

| Id | Journey | Pri | When | Device | Build / vC | Date | Result |
|---|---|---|---|---|---|---|---|
| **Part A. Gaps left by automation** | | | | | | | |
| M-01 | Paid online translators with a real key on the Play build | P0 | L | phone + Xiaomi tablet | | | |
| M-02 | Gutenberg catalog on the release build | P0 | L | phone | | | |
| M-03 | ML Kit model download on a real device | P1 | C | phone | | | |
| M-04 | Crashlytics reports a release build | P0 | L | console | | | |
| M-05 | Re-anchoring while rows grow after a jump (ScrollHold) | P0 | L | Xiaomi tablet | | | |
| M-06 | Device specifics: MIUI, Android 15 edge-to-edge, tablet taskbar, large books | P0 | L | all | | | |
| **Part B. Manual journeys of the catalogue** | | | | | | | |
| J-LIB-09 | Email account lifecycle | P0 | L | phone + tablet | | | |
| J-LIB-11 | Buying Premium (Play license tester) | P0 | L | phone | | | |
| J-LIB-12 | Premium: reinstall, second account, refund, backup, debug override | P0 | L | 2 devices | | | |
| J-LIB-13 | Google account and Drive on a Play build | P0 | L | phone + tablet | | | |
| J-LIB-14 | A reading week: Almanac, saved words with audio, screen fit | P1 | C | phone + tablet | | | |
| J-LIB-15 | In-app update between two Play builds | P0 | L | phone | | | |
| J-LIB-16 | Upgrading an old install in place (DB v2 / v3) | P0 | L | tablet AVD | | | |
| J-LIB-17 | Release artifact audit | P0 | L | build machine + phone | | | |
| J-LIB-18 | Odd files that need a human eye | P1 | C | tablet | | | |
| J-RDR-10 | Tablet: divider by hand, split-screen | P1 | C | tablet | | | |
| J-RDR-11 | Phone: stacked reading, words, rotation | P0 | L | phone | | | |
| J-RDR-12 | Orientation lock and first-run defaults | P0 | L | phone + tablet | | | |
| J-RDR-13 | Illustrated book keeps the place | P0 | L | tablet | | | |
| J-RDR-14 | Upgrade from a pre-v6 build | P0 | L | phone | | | |
| J-RDR-15 | Legacy migration edge cases | P1 | C | phone | | | |
| J-RDR-16 | Phone: system bars, themes, keyboard on Android 15 | P0 | L | phone | | | |
| J-RDR-17 | Tablet: rail and taskbar insets | P0 | L | tablet | | | |
| J-RDR-18 | Paid engine: hidden translation stops metered requests | P0 | L | any | | | |
| J-RDR-19 | Release visual regression pass (goldens) | P1 | C | build machine | | | |
| J-RDR-20 | Known-bug probe | P2 | C | tablet | | | |
| J-TRN-11 | A DeepL reader | P0 | L | phone or tablet | | | |
| J-TRN-12 | Paid chapter fill, switching paid engines | P1 | C | tablet | | | |
| J-TRN-13 | Reading on a bad connection | P1 | C | phone | | | |
| J-TRN-14 | Broken packs, full storage, stalled server | P1 | C | debug AVD + proxy | | | |
| J-TRN-15 | Upgrades, restores, 32-bit, security audit | P1 | C | 2 phones + AVD | | | |
| J-TRN-16 | Real-device feel: first ML Kit download, gestures, speech | P1 | C | phone + tablet | | | |
| **Part C. Manual halves of automated journeys** | | | | | | | |
| C-01 | Visual-only checkpoints inside automated journeys | P1 | C | tablet + phone | | | |

Mark every P0 whose purchase or sign-in path was NOT run on a Play-installed build as "not run" in Result.

---

# Part A. Gaps left by automation

### M-01. Paid online translators with a real key on the Play build (P0, L)

Why: this is the only coverage of the reflective Gson DTOs and Retrofit services of DeepL, Google Cloud and
Azure under R8. The release smoke R-06 covers Quick Translate only (no key). Build: **installed from Play** (R8),
real keys, free tiers are enough. Device: phone and Xiaomi tablet (one engine each is acceptable, all three
engines at least once). Also see J-TRN-11 (DeepL in depth), J-TRN-12 (Azure region, engine switching).

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Reader on `en-novel.epub` → Translator picker → DeepL → key → Save | no "API key isn't set" flash; paragraphs translate in DeepL's wording; no "TRANSLATION FAILED" (ONL-003, ONL-006) | ☐ | ☐ | |
| 2 | Open the picker again, and Settings → Translation | under DeepL "{used} / {limit} this month" with numbers; **usage grows** after another screen is translated; Settings shows the same numbers (ONL-015, SET-004) | ☐ | ☐ | |
| 3 | Long-press a word, wait for Ready, Save | popup translation shown; Words holds the same text; counter grew once for the popup (POP-009) | ☐ | ☐ | |
| 4 | Picker → Google Cloud → key → Save; translate the next screen | paragraphs translate; Google's usage line appears and grows; DeepL's does not (ONL-019) | ☐ | ☐ | |
| 5 | Picker → Azure → key + Region (e.g. `westeurope`) in the Region field → Save; translate a screen | translates, no region-mismatch error; Azure usage grows (ONL-012, ONL-019) | ☐ | ☐ | |
| 6 | For each engine: enter a wrong key | banner "Invalid … API key", no translated text, no silent fall back to ML Kit (ONL-002) | ☐ | ☐ | |
| 7 | Force-stop, relaunch, reader | key survives (Keystore), engine still translates; no freeze (SET-010, SET-012) | ☐ | ☐ | |
| 8 | Select Offline HQ again | restored | ☐ | ☐ | |

A failure on 1, 4 or 5 on the Play build but not on a debug build = R8 stripped a DTO: check `proguard-rules.pro`.

### M-02. Gutenberg catalog on the release build (P0, L)

Why: release smoke R-04 uses Standard Ebooks; the Gutenberg OPDS Retrofit/Jsoup path was void on the test machine
(HTTP 503). Build: Play or release build, real network (try both Wi-Fi and mobile data once). Device: phone.
Cases: CAT-001..CAT-006, J-LIB-13 step 9.

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Catalog → Project Gutenberg → search "Pride and Prejudice" | result list with titles, authors, covers; no error (CAT-006) | ☐ | ☐ | |
| 2 | Open the book row → download | progress, then the book opens in the reader (top bar shows title); EPUB stored under app-private `files/catalog/` | ☐ | ☐ | |
| 3 | Translate a paragraph in it | translation appears | ☐ | ☐ | |
| 4 | Back to Library | one new card; counts toward the free-tier limit (3 books, free) | ☐ | ☐ | |
| 5 | Search for something with no result ("zzzzqqq") | friendly empty state, no crash | ☐ | ☐ | |
| 6 | Aeroplane mode, search | clear "No connection" style message, no crash | ☐ | ☐ | |
| 7 | Standard Ebooks tab, open any book | downloads and opens (control: R-04 equivalent by hand) | ☐ | ☐ | |

### M-03. ML Kit model download on a real device (P1, C)

Why: ML Kit model download does not work on the emulator. Device: phone (mid/low-end preferred), online, model
not yet downloaded (clear Google Play services' ML Kit data or use a new target pair). See J-TRN-16 steps 1-4.

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Settings → Translation → ML Kit; target Spanish; open `en-novel.epub` at an untranslated chapter | "Downloading model" indicator, then translations arrive (MLK-004). Known bug: only "Translating…" up to ~90 s, no percent (MLK-005) | ☐ | ☐ | |
| 2 | Leave the screen alone while shimmers turn into text | bottom panel does not jump or drift (MLK-008) | ☐ | ☐ | |
| 3 | Aeroplane mode, new paragraphs in the same pair | still translates (model on device) (MLK-011) | ☐ | ☐ | |
| 4 | Offline HQ, target Portuguese, long-press a word | popup "Downloading language model… N%", then Ready (POP-015) | ☐ | ☐ | |
| 5 | Record time and size of the first download | note: ______ s / ______ MB | ☐ | ☐ | |

### M-04. Crashlytics reports a release build (P0, L; console check only)

There is no safe way to force a crash on a Play build without shipping a crash trigger, so this is a console check.
J-LIB-17 step 7 is the non-fatal path.

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Firebase console → Crashlytics → select the new versionName | the build appears (sessions counted after the first launches from the tester devices); crash-free stats present | ☐ | ☐ | |
| 2 | Crashlytics → open any event of the release (or a non-fatal from a failed parse, e.g. open a corrupt book on the release build once) | stack trace is **de-obfuscated** (class and method names readable): the R8 mapping uploaded (ERR-014) | ☐ | ☐ | |
| 3 | Same check for a debug install | no events from debug builds (collection is release-only) | ☐ | ☐ | |
| 4 | Play Console → Android vitals | no new ANR / crash cluster for this versionCode | ☐ | ☐ | |

### M-05. Re-anchoring while rows grow after a jump (P0, L)

Why: ScrollHold re-anchors for up to 10 s while rows settle; the automated J-RDR-08 lands outside that window
(demoted to MANUAL). Device: Xiaomi tablet, landscape, side-by-side. Network: slow or throttled if possible.
Cases: POS-*, BMK-006, J-RDR-08, CLAUDE.md "Reader jumps".

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Open a long book (F-LONG); place a bookmark mid-chapter on a paragraph; go to chapter 1 or the Library | | ☐ | ☐ | |
| 2 | Delete the EN→RU Offline HQ pack (Settings → Language packs) or use a metered engine on a slow link so translations arrive late | translations are NOT ready at the landing | ☐ | ☐ | |
| 3 | Bookmarks → tap the bookmark; watch for ~10 s without touching | the bookmarked paragraph stays at the top while the right pane rows grow; no creeping, no jump back to the chapter start | ☐ | ☐ | |
| 4 | Repeat with a bookmark in the **last** chapter, near the end of the book | the list does not clamp early and save a wrong position; reopening restores the bookmarked place | ☐ | ☐ | |
| 5 | Repeat with Chapter picker jump and with the "Show illustrations" toggle on an illustrated book (F-ALICE) | masthead / same paragraph stays on top; settles | ☐ | ☐ | |
| 6 | During the 10 s window, touch-scroll by hand | re-anchoring stops at once; the app never scrolls against the finger | ☐ | ☐ | |
| 7 | Phone, stacked layout: repeat step 3 | same | ☐ | ☐ | |

### M-06. Device specifics (P0, L)

| # | Device | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|---|
| 1 | Xiaomi tablet (MIUI / HyperOS) | Fresh install from Play; open Library, Reader, Settings, Words | nothing under the status/nav bar or the MIUI gesture line; rail icons reachable; MIUI's battery saver / autostart prompts do not kill the app mid-read | ☐ | ☐ | |
| 2 | Xiaomi tablet | Rotate in the reader, split-screen with another app, back | layout switches live; position kept (J-RDR-10 steps 8-10) | ☐ | ☐ | |
| 3 | Xiaomi tablet | Notification shade, floating window, screenshot while a word popup is open | popup does not stick (POP-008); note: MIUI screenshots lack the status bar (not an app bug) | ☐ | ☐ | |
| 4 | Pixel 7 phone, Android 15 | J-RDR-16 steps 1-8 (edge-to-edge, icon colours per theme, gesture and 3-button nav, keyboard) | all ✓ in J-RDR-16 | ☐ | ☐ | |
| 5 | Tablet with persistent taskbar | J-RDR-17 steps 1-3 | account button and last list items above the taskbar | ☐ | ☐ | |
| 6 | Any real device | Import F-ILIAD (huge chapter) and a ≥ 20 MB EPUB | opens without "Book is too large"; time until scrollable: ______ s (known bug IMP-011, record only); no ANR, no OOM; scrolling stays smooth after it opens | ☐ | ☐ | |
| 7 | Any real device | Open the large book, translate a screen, rotate, background the app 5 min, return | position kept; no crash | ☐ | ☐ | |
| 8 | Low-RAM phone if available | Same as 6 | no OOM crash in Crashlytics for this run | ☐ | ☐ | |

---

# Part B. Manual journeys of the catalogue

Steps are condensed; the full wording and fixtures are in `docs/e2e/e2e_journeys.md` under the same id.
Fixtures: F-* names as in that file.

## J-LIB-09. Email account lifecycle (P0, L; phone + tablet; fresh install, F-ALICE imported, signed out)

Automated part (J-LIB-09a: steps 1-3, 6, 12, screens only) does not touch Firebase; this runs against real Firebase.
Verification, reset and deletion belong here.

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Avatar → sign in → Create account; email "not-an-email" | inline "Enter a valid email address." (ACC-002) | ☐ | ☐ | |
| 2 | Valid throwaway email, password "short1" | "Password must be at least 8 characters." (ACC-003) | ☐ | ☐ | |
| 3 | Password "Mirrolit-e2e-1" → Create account | signed in; Profile "Email not verified"; verification mail arrives (ACC-001) | ☐ | ☐ | |
| 4 | Profile → Resend | confirmation with spam-folder note (ACC-010) | ☐ | ☐ | |
| 5 | Open the mail link in a browser; back to the app → Refresh | "Email verified" without signing out (ACC-011) | ☐ | ☐ | |
| 6 | Avatar on phone bottom bar and on tablet rail | present and tappable (ACC-020) | ☐ | ☐ | |
| 7 | Sign out | signed-out state (ACC-015) | ☐ | ☐ | |
| 8 | Create account with an existing email | "An account with this email already exists." (ACC-004) | ☐ | ☐ | |
| 9 | Sign in, wrong password | "Incorrect email or password."; still signed out (ACC-006) | ☐ | ☐ | |
| 10 | Forgot password: registered address, then unregistered | same neutral message both times (ACC-012); the reset mail arrives; the new password works | ☐ | ☐ | |
| 11 | Sign in with the throwaway address | "Signed in" (ACC-005) | ☐ | ☐ | |
| 12 | Home → Catalog → Words → Home | Profile is not resurrected (ACC-019) | ☐ | ☐ | |
| 13 | Profile → Delete account → confirm | re-authentication prompt; no deletion without the password (ACC-013) | ☐ | ☐ | |
| 14 | Enter the password | "Account deleted"; signed out; Alice still in Library (ACC-014) | ☐ | ☐ | |
| 15 | Sign in with the deleted address | "No account found for that email." (ACC-007) | ☐ | ☐ | |

## J-LIB-11. Buying Premium (P0, L; phone, Play-installed; license tester; 3 books in the library)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Play Console → In-app products | `premium_unlimited` exists, Active, id matches `BillingManager.PREMIUM_PRODUCT_ID` (PRM-015) | ☐ | ☐ | |
| 2 | Force-stop, relaunch, within 1 s import a 4th book, Upgrade | "Store isn't ready yet. Try again in a moment.", no crash (PRM-010) | ☐ | ☐ | |
| 3 | Wait ~5 s, import again | button "Upgrade · <price>" real price; Play sheet opens (PRM-001, PRM-010) | ☐ | ☐ | |
| 4 | In the sheet press Back | "Purchase failed. Please try again."; still free (PRM-011) | ☐ | ☐ | |
| 5 | Pay with "Slow test card, declines after a few minutes" | "Purchase pending…"; 4th book still blocked; after decline still free (PRM-009) | ☐ | ☐ | |
| 6 | Pay with "Test card, always approves" | "Premium unlocked — enjoy your unlimited library."; 4th book imports (PRM-002) | ☐ | ☐ | |
| 7 | Force-stop, relaunch, import 5th and 6th | no limit dialog; Settings shows Premium active (PRM-003) | ☐ | ☐ | |
| 8 | Aeroplane mode, force-stop, relaunch, import 7th | Premium active offline (PRM-014) | ☐ | ☐ | |
| 9 | Walk every Settings section | no "Premium (debug)" toggle (PRM-005) | ☐ | ☐ | |

## J-LIB-12. Premium across reinstall, second account, refund, backup, debug override (P0, L; two real devices + a debug build)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Device B (never purchased): Settings → Premium → Restore purchase | "No previous purchase found."; free tier (PRM-012) | ☐ | ☐ | |
| 2 | Device B: Upgrade with the always-approves card | "Premium active · unlimited library"; 4th book imports (PRM-008) | ☐ | ☐ | |
| 3 | Device A (purchased): uninstall, reinstall from Play, import 3 books, Restore purchases | "Premium active · unlimited library" (PRM-004) | ☐ | ☐ | |
| 4 | Device A: back up now (`bmgr backupnow io.mirrolit.app`); restore onto a profile of a non-purchasing account, aeroplane mode, open app | Premium NOT shown; 4th import blocked until Play's sync (PRM-007) | ☐ | ☐ | |
| 5 | Play Console → refund B's order, "Remove entitlement"; relaunch B online | Premium reverts; limit dialog again (PRM-006) | ☐ | ☐ | |
| 6 | Debug build: Premium (debug) on → 5 books; off → another | no dialog; then dialog (PRM-013) | ☐ | ☐ | |
| 7 | Debug build, email account + debug premium: Delete account → re-auth | "Account deleted"; no entitlement survives; reading data stays (ACC-014) | ☐ | ☐ | |

## J-LIB-13. Google account and Drive on a Play build (P0, L; phone + tablet; ≤ 2 books; accounts `ACC_PW`, `ACC_UNVERIFIED`)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Aeroplane mode, sign in with email | "No connection. Check your network and try again."; no partial state (ACC-017) | ☐ | ☐ | |
| 2 | Continue with Google, pick the device account | signed in, Profile shows verified email; no "not registered to use OAuth2.0" error on the Play-signed build (ACC-008, ACC-018) | ☐ | ☐ | |
| 3 | Catalog → FROM YOUR DRIVE → pick a valid EPUB | downloads, opens, appears in Library (CAT-010); add a bookmark in chapter 2 | ☐ | ☐ | |
| 4 | Drive picker → Back | "Drive access was cancelled." or nothing; no library entry (CAT-013) | ☐ | ☐ | |
| 5 | Drive → `notes.pdf` | "That file isn't a supported book (EPUB, FB2, or MOBI)."; nothing added (CAT-011) | ☐ | ☐ | |
| 6 | Drive → large EPUB, aeroplane mode mid-download | "Couldn't load the file from Drive. Check your connection and try again." (CAT-014) | ☐ | ☐ | |
| 7 | Re-import the step-3 EPUB | opens without limit dialog; bookmark still listed (LIB-005) | ☐ | ☐ | |
| 8 | Look over Catalog and Drive | no Google Play Books import path (CAT-015) | ☐ | ☐ | |
| 9 | Catalog → Gutenberg search "Pride and Prejudice" → download | appears in Library (CAT-006); see M-02 | ☐ | ☐ | |
| 10 | Debug build: repeat 3 and 9; `adb shell run-as io.mirrolit.app ls -R files/`, `ls /sdcard/Download` | files under app-private `files/`; nothing in shared storage; no storage write permission (ERR-016) | ☐ | ☐ | |
| 11 | Sign out; Continue with Google with the address of `ACC_PW` | collision message, no duplicate account (ACC-009) | ☐ | ☐ | |
| 12 | Sign in as `ACC_UNVERIFIED` → "Email not verified"; open mail link; back; Refresh | "Email verified" without signing out (ACC-011) | ☐ | ☐ | |
| 13 | Library at "3 VOLUMES"; Drive → pick the step-3 EPUB again | opens at its place, no limit dialog; still 3 (CAT-016) | ☐ | ☐ | |
| 14 | Delete the test accounts | done | ☐ | ☐ | |

## J-LIB-14. A reading week on real hardware (P1, C; Pixel 7 phone + tablet with taskbar; fresh install; DeepL key)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Day 1: read F-ALICE (RU) 10 min → Almanac | "<N> ACTIVE DAYS · <M> MIN", M matches; today highlighted (ALM-007) | ☐ | ☐ | |
| 2 | Read F-FB2 (DE) 5 min → Almanac breakdowns | per-book and per-language minutes non-zero and attributed correctly (ALM-008) | ☐ | ☐ | |
| 3 | Day 2 (next day or advanced date): read a few minutes | streak 2; encouragement text; weekly bars for both days (ALM-006, ALM-003) | ☐ | ☐ | |
| 4 | DeepL key; long-press "curious", Save, open the word in Words | stored text equals the popup's; no second translation request (WRD-007) | ☐ | ☐ | |
| 5 | Word detail → Listen | spoken at the configured rate/pitch (WRD-011) | ☐ | ☐ | |
| 6 | Phone: Home, Catalog, Almanac, Settings, Account, Profile, Words, each scrolled to the bottom | no vertical "ribbon of letters", no overlaps, nothing cut off (LIB-016) | ☐ | ☐ | |
| 7 | Tablet with taskbar: rail scrolled to the bottom | account button visible above the taskbar (ACC-021) | ☐ | ☐ | |
| 8 | Tablet: import F-ILIAD, open the huge chapter | record time until interactive: ______ s (IMP-011 known bug) | ☐ | ☐ | |

## J-LIB-15. In-app update between two Play builds (P0, L; real device installed from Play with build N; N+1 rolled out; a sideloaded second device)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Home | banner "A new version of Mirrolit is available" with Update / Not now (UPD-001) | ☐ | ☐ | |
| 2 | Not now; relaunch | banner hidden for this versionCode (3 days); About still says available (UPD-003) | ☐ | ☐ | |
| 3 | Settings → About → Update | "Downloading update… N%" (UPD-006) | ☐ | ☐ | |
| 4 | While downloading press Home, wait 20 s, return | reattaches; no crash; one banner; progress kept (UPD-005) | ☐ | ☐ | |
| 5 | When ready | About shows Restart, Home banner offers Restart; Restart relaunches on N+1 (UPD-002, UPD-006) | ☐ | ☐ | |
| 6 | Open F-ALICE | position, bookmark and translation work (UPD-002) | ☐ | ☐ | |
| 7 | Compare every book's cover/progress/place noted before step 3 | all present, covers not blank or swapped, same progress, bookmark listed (DAT-007) | ☐ | ☐ | |
| 8 | Sideloaded device: open ≥ 3 times | no update banner; no crash (UPD-004) | ☐ | ☐ | |

## J-LIB-16. Upgrading an old install in place (P0, L; tablet AVD API 35; old DB-v2 and DB-v3 APKs)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Install v2 APK; import F-ALICE; translate one paragraph; note it | | ☐ | ☐ | |
| 2 | `adb install -r` current build; open | no data reset; Alice in Library; the paragraph translates instantly from cache (DAT-001) | ☐ | ☐ | |
| 3 | Add a bookmark, reopen Bookmarks | listed, no crash (DAT-002) | ☐ | ☐ | |
| 4 | Make Alice most recent (hero); re-import `alice-illustrated.epub` | empty centre text but intact layout; synopsis appears after re-import (DAT-004) | ☐ | ☐ | |
| 5 | Wipe; install v3 APK; F-ALICE, F-PROSE; 1 bookmark, 1 note, 1 saved word; F-PROSE to ch. 4 | noted | ☐ | ☐ | |
| 6 | `adb install -r` current build | bookmark, note, word, session present on the right book, no duplicates (DAT-003) | ☐ | ☐ | |
| 7 | Open F-PROSE | same chapter and % (DAT-006, IMP-009) | ☐ | ☐ | |
| 8 | Release build: check `fallbackToDestructiveMigration` is debug-only (or unknown schema version) | fails loudly, library not wiped (DAT-005) | ☐ | ☐ | |

## J-LIB-17. Release artifact audit (P0, L; build machine + one real device)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Blank `billingPublicKey`, `./gradlew :app:bundleRelease`; restore the key | build fails with the guard (ERR-012) | ☐ | ☐ | |
| 2 | `bundleRelease`; `jarsigner -verify -verbose`; check mapping.txt | signed with the release key; minify and shrink on (ERR-011) | ☐ | ☐ | |
| 3 | Install via bundletool; `pm list packages \| grep mirrolit` | `io.mirrolit.app`, never `com.example.splitreader` (ERR-010) | ☐ | ☐ | |
| 4 | Settings, all sections | no "Premium (debug)" (PRM-005; also automated by R-01) | ☐ | ☐ | |
| 5 | jadx the release APK, grep for keys/tokens and `http://` | no secrets; HTTPS base URLs; logging interceptor debug only (ERR-013) | ☐ | ☐ | |
| 6 | Search for `READ_MEDIA_DOCUMENT_VISUAL_USER_SELECTED` | real call site tied to the picker on API 33+ (ERR-017) | ☐ | ☐ | |
| 7 | Non-fatal on debug, then on release | Crashlytics shows only the release event; mapping uploaded (ERR-014); see M-04 | ☐ | ☐ | |
| 8 | Save a translator key, `bmgr backupnow io.mirrolit.app`, inspect the set | `translator_keys_enc` excluded; no plaintext secret (ERR-015) | ☐ | ☐ | |
| 9 | Open the privacy policy URL from the Play listing / Console | loads; contact and content current (ERR-009) | ☐ | ☐ | |

## J-LIB-18. Odd files that need a human eye (P1, C; tablet debug build, Premium (debug) on)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Import `palmdoc-distance0.mobi`, read near the distance-0 token | decodes correctly, no garbage (IMP-012) | ☐ | ☐ | |
| 2 | Import `full-features.xml` through the picker | opens as proper FB2, not raw XML (PRS-015) | ☐ | ☐ | |
| 3 | Import `empty-first-chapter.epub` | valid empty-chapter state; can go to chapter 2 (PRS-025) | ☐ | ☐ | |
| 4 | Real tablet: `iliad-el.epub`, book 1 | record time to responsive scrolling: ______ s (IMP-011 known bug) | ☐ | ☐ | |

## J-RDR-10. Tablet: divider by hand, then split-screen (P1, C; real tablet landscape, F-ALICE)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Tap the grip strip over the divider | bars toggle; ratio unchanged (LAY-008) | ☐ | ☐ | |
| 2 | Press the grip, drag vertically | list scrolls; divider stays (LAY-009) | ☐ | ☐ | |
| 3 | Hold the grip, drag left slowly, release | follows live; settles once, no jump back (LAY-003) | ☐ | ☐ | |
| 4 | Drag far left, then far right | stops at 0.3 / 0.7 (LAY-005) | ☐ | ☐ | |
| 5 | Display dialog: Split ratio slider to both ends | same bounds (LAY-005) | ☐ | ☐ | |
| 6 | Text size, Line height, Page margins to each end | stop at documented min/max; number matches (APP-015) | ☐ | ☐ | |
| 7 | Drag the grip, add a second finger / pull the shade | dragged ratio kept (LAY-007) | ☐ | ☐ | |
| 8 | Note a plate's place; split-screen with another app below 600dp | stacked live, device still landscape (LAY-024) | ☐ | ☐ | |
| 9 | Find the same plate | same chapter/anchor (ILL-017) | ☐ | ☐ | |
| 10 | Widen back past 600dp | side-by-side returns (LAY-024) | ☐ | ☐ | |
| 11 | Narrowest split / freeform: drag divider to an extreme | no crash, no blank/NaN layout; clamps (LAY-027) | ☐ | ☐ | |

## J-RDR-11. Phone: stacked reading, words, rotation (P0, L; Pixel 7 phone, Auto orientation; F-ALICE, F-LONG; TTS voice)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | F-LONG portrait; dismiss hint with "Got it" | | ☐ | ☐ | |
| 2 | Rotate portrait, landscape, portrait | hint does not return (APP-027); layout stacked, side-by-side, stacked, aligned (NAV-004) | ☐ | ☐ | |
| 3 | Drag the horizontal divider, release | heights follow and stay (LAY-011) | ☐ | ☐ | |
| 4 | Fully up, fully down | stops at 0.3 / 0.7 (LAY-014) | ☐ | ☐ | |
| 5 | Long-press a word in the top panel; Save; TTS | selection; saved to Words; spoken (LAY-016) | ☐ | ☐ | |
| 6 | Long-press a word in the bottom panel; tap there | no selection; tap toggles bars (LAY-015) | ☐ | ☐ | |
| 7 | Fling top; drag bottom | other panel follows; paragraphs aligned (LAY-017) | ☐ | ☐ | |
| 8 | Alternate top and bottom scrolls ×5 | no oscillation (LAY-018) | ☐ | ☐ | |
| 9 | Fling top, grab bottom mid-deceleration | smooth takeover, no desync (LAY-019) | ☐ | ☐ | |
| 10 | Rotate twice quickly mid-sync | no crash; sync re-establishes (LAY-026) | ☐ | ☐ | |
| 11 | F-ALICE plate | top shows the picture, bottom a slim placeholder (LAY-020) | ☐ | ☐ | |
| 12 | Scroll past 3+ plates | stays aligned, no drift (ILL-013) | ☐ | ☐ | |

## J-RDR-12. Orientation lock and first-run defaults (P0, L; real phone + tablet, auto-rotate on)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Fresh install on phone | Orientation = Portrait; rotation keeps portrait (APP-019) | ☐ | ☐ | |
| 2 | Fresh install on tablet | Orientation = Landscape; stays (APP-020) | ☐ | ☐ | |
| 3 | Phone: Auto; rotate all four ways | follows (APP-021) | ☐ | ☐ | |
| 4 | Landscape (incl. upside-down), then Portrait | stays in each (APP-021) | ☐ | ☐ | |
| 5 | F-LONG with Portrait / Landscape / Auto selected | reader stays / stays / follows (APP-025) | ☐ | ☐ | |
| 6 | Words: select a word, rotate (Auto) | still selected; side by side when wide (NAV-015) | ☐ | ☐ | |
| 7 | Seed orientation pref `SIDEWAYS` (`run-as`); launch | no crash; Portrait on phone, Landscape on tablet (APP-030) | ☐ | ☐ | |
| 8 | Seed legacy theme `DEFAULT`; launch, F-LONG, Settings | theme "Paper", no crash (APP-018) | ☐ | ☐ | |

## J-RDR-13. Illustrated book keeps the place (P0, L; tablet landscape, network; F-ALICE, F-IMGFILES; EN→RU)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Import F-IMGFILES; chapter picker | image-only files are not chapters; pictures open the next chapter (ILL-012) | ☐ | ☐ | |
| 2 | Open F-ALICE; note session start | | ☐ | ☐ | |
| 3 | Scroll past several plates in chapter 2; note the top paragraph | | ☐ | ☐ | |
| 4 | Bookmark the paragraph right after a plate | icon on | ☐ | ☐ | |
| 5 | Show illustrations off, then on | same paragraph on top (POS-015); bookmark still on it (BMK-010) | ☐ | ☐ | |
| 6 | Library, reopen | same paragraph (POS-015) | ☐ | ☐ | |
| 7 | Target EN→RU to EN→DE | retranslation at the visible paragraph (POS-014) | ☐ | ☐ | |
| 8 | Back; session "paragraphs read" | equals forward distance, ≥ 0 (POS-017) | ☐ | ☐ | |
| 9 | Read ~5 forward; jump to a chapter-1 bookmark; Back | never negative (POS-018) | ☐ | ☐ | |
| 10 | Bookmark on a plate; go to chapter 1; tap it | lands on the plate, not one item off (ILL-014) | ☐ | ☐ | |

## J-RDR-14. Upgrade from a pre-v6 build (P0, L; phone or tablet; Play v5 with F-LEGACY data; second legacy profile)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | On v5 note F-LONG's and F-ALICE's top paragraphs and all bookmark labels/dates | | ☐ | ☐ | |
| 2 | `adb install -r` current build; launch | Night, Mono, size 22, split 0.4, illustrations off unchanged (APP-005) | ☐ | ☐ | |
| 3 | Open F-LONG | text edge 12dp from screen edge (APP-005); same paragraph as on v5 (POS-002, POS-020) | ☐ | ☐ | |
| 4 | Bookmarks | label and creation time kept (BMK-009); two bookmarks on one paragraph appear once, earlier kept (BMK-008) | ☐ | ☐ | |
| 5 | Tap each old bookmark | each lands where it did on v5 (BMK-006) | ☐ | ☐ | |
| 6 | Reopen F-LONG twice more | position does not drift (POS-021) | ☐ | ☐ | |
| 7 | F-ALICE, illustrations on | chapter numbers match v5; same paragraph (ILL-002) | ☐ | ☐ | |
| 8 | Second legacy profile, margins never touched | default 32dp reproduces the v5 look (APP-004) | ☐ | ☐ | |

## J-RDR-15. Legacy migration edge cases (P1, C; phone Auto orientation; dev build for step 7)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Install over v5; F-ALICE bookmark just past a plate: list, then tap | preview and "¶ n" right; one scroll (POS-009) | ☐ | ☐ | |
| 2 | Fresh v5 data; F-LONG; rotate ×3 during first load | no crash; bookmarks once each (POS-010) | ☐ | ☐ | |
| 3 | Fresh v5 data; open F-LONG, swipe app away within 2 s; relaunch | position and bookmarks consistent (POS-012) | ☐ | ☐ | |
| 4 | v5 data with illustrations shown; turn them off before first opening F-ALICE | opens; position correct or off by at most the plates above it (ILL-018) | ☐ | ☐ | |
| 5 | Import F-EMPTY1 | no crash; opens at (chapter, 0, 0) (POS-024) | ☐ | ☐ | |
| 6 | Restore a pre-migration backup (or clear the migrated flag); open the book | migration reruns, same position (POS-025) | ☐ | ☐ | |
| 7 | Dev build: kill between position write and bookmarks write; relaunch | position stable; bookmarks not migrated twice (POS-022) | ☐ | ☐ | |

## J-RDR-16. Phone: system bars, themes, keyboard on Android 15 (P0, L; Pixel 7 phone API 35, gesture nav first; Paper)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Launch on Library | edge-to-edge; nothing under the bars; status strip below the clock (SYS-001, SYS-003) | ☐ | ☐ | |
| 2 | Open F-LONG | first line clear of the status bar (SYS-005); dark icons on Paper (SYS-002) | ☐ | ☐ | |
| 3 | Display: Sepia, Night, AMOLED | icons dark, light, light, immediately (SYS-002) | ☐ | ☐ | |
| 4 | Tap to show the footer | labels above the gesture bar; background to the edge (SYS-006) | ☐ | ☐ | |
| 5 | 3-button navigation; last line of a chapter; footer | content above buttons; background to the edge (SYS-006) | ☐ | ☐ | |
| 6 | Settings | edge-to-edge, not obscured (SYS-001) | ☐ | ☐ | |
| 7 | Words search field | visible above the keyboard; bottom bar not over the IME (NAV-016) | ☐ | ☐ | |
| 8 | Catalog search field | same (NAV-016) | ☐ | ☐ | |

## J-RDR-17. Tablet: rail and taskbar insets (P0, L; real tablet with persistent taskbar; AVDs API 26/30/33/35/37; signed out)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Launch on the real tablet | status strip below the system status bar (SYS-004) | ☐ | ☐ | |
| 2 | Bottom of the rail | account button / "Sign in" fully above the taskbar (SYS-007) | ☐ | ☐ | |
| 3 | Scroll Library and Settings to the bottom | last items above the taskbar (SYS-007) | ☐ | ☐ | |
| 4 | Cutout device | rail background to the edge; icons clear of the cutout (SYS-009) | ☐ | ☐ | |
| 5 | Split-screen to ~450-528dp height (icon rail) | bottom rail icon inside the rail (SYS-010) | ☐ | ☐ | |
| 6 | Steps 1-3 on AVD API 26, 30, 33, 35, 37 (after `gradle --stop`) | nothing under the taskbar on any API (SYS-008): 26 ☐ 30 ☐ 33 ☐ 35 ☐ 37 ☐ | ☐ | ☐ | |

## J-RDR-18. Paid engine: hiding the translation stops metered requests (P0, L; any device; paid key; not Offline HQ)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Open F-LONG; note the usage counter | translations arrive | ☐ | ☐ | |
| 2 | Display dialog: Show translation off | single pane | ☐ | ☐ | |
| 3 | Scroll 5 new screens | counter has not grown (APP-016) | ☐ | ☐ | |
| 4 | Show translation on | current screen translates; counter grows (APP-016) | ☐ | ☐ | |
| 5 | Settings: Show translation off; open another book; scroll | single pane; counter unchanged (APP-017) | ☐ | ☐ | |
| 6 | Restore Show translation on | | ☐ | ☐ | |

## J-RDR-19. Release visual regression pass (P1, C; build machine + tablet emulator)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Run the full screenshot/golden suite | pixel-identical incl. AppShell-tagged goldens (SYS-013) | ☐ | ☐ | |
| 2 | Review the 8 landscape reader goldens (Paper/Night × 1x/1.3x × LTR/RTL, split 0.35) | identical to the pre-stacked baseline (LAY-025) | ☐ | ☐ | |
| 3 | Tablet emulator: RTL book, landscape, split 0.35, Night, 1.3x | matches the golden by eye (LAY-025) | ☐ | ☐ | |

## J-RDR-20. Known-bug probe (P2, C; real tablet, gesture navigation; F-LONG "Reading" with progress). Report, never block.

| # | Do | Expect (known) | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Chapter picker: pick the chapter already on screen; tap bookmark icon | placed on the jump target; known: may act on a stale target (BMK-011) | ☐ | ☐ | |
| 2 | Double-tap the bookmark icon fast; open Bookmarks | one bookmark; known: may show two (BMK-012) | ☐ | ☐ | |
| 3 | Scroll to the very end, watch the bookmark icon | no flicker; known: flashes (BMK-013) | ☐ | ☐ | |
| 4 | Margins 4dp then 48dp, F-ALICE at a plate | plates align; known: plates keep a 32dp inner padding (ILL-010) | ☐ | ☐ | |
| 5 | Bottom of the rail | gap above the gesture bar; known: avatar can touch it (SYS-011) | ☐ | ☐ | |
| 6 | Delete F-LONG, import again | by design still "Reading" with the old % (v29: the removed book keeps its place); document (BMK-014) | ☐ | ☐ | |

## J-TRN-11. A DeepL reader (P0, L; real device with IPv4/IPv6; valid DeepL key; Offline HQ current; `en-novel.epub` saved deep in chapter 4)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Picker → DeepL → key → Save, return at once | no "API key isn't set" flash; DeepL current only after save (ONL-006, SET-013) | ☐ | ☐ | |
| 2 | First screen | DeepL wording, no error (ONL-003) | ☐ | ☐ | |
| 3 | Scroll several pages | every page translates (ONL-005) | ☐ | ☐ | |
| 4 | Picker, then Settings → Translation | "{used} / {limit} this month"; Settings same numbers (ONL-015, SET-004) | ☐ | ☐ | |
| 5 | Long-press a word, Ready, Save | counter grew by the popup's one request only; Words holds the popup's text (POP-009, WRD-007) | ☐ | ☐ | |
| 6 | Long-press, drag end handle into the margin, release | counter +1 (POP-006) | ☐ | ☐ | |
| 7 | Show translation off; long-press a word; scroll 5 pages | popup translates (POP-002); counter grows only for the popup (TRN-010) | ☐ | ☐ | |
| 8 | Show translation on | screen translates; counter grows (TRN-010) | ☐ | ☐ | |
| 9 | Rotate there and back twice | shown paragraphs reappear at once; counter unchanged (ONL-023) | ☐ | ☐ | |
| 10 | Pane off; open a never-opened book; read 3 screens; pane on | counter did not grow while hidden; grows only by the visible screen (TRN-027) | ☐ | ☐ | |
| 11 | Reset icon next to usage → Cancel | "Reset counter" dialog (real quota not affected); unchanged (ONL-017) | ☐ | ☐ | |
| 12 | Reset and confirm | usage reads 0; nothing claims the remote quota changed (ONL-018) | ☐ | ☐ | |
| 13 | Force-stop, relaunch; Reader, Settings, Home | each appears immediately, no freeze; DeepL still configured and translating (SET-012, SET-010) | ☐ | ☐ | |
| 14 | Library, reopen the book | only the visible page requested; nothing for chapter 0 / paragraph 0 (TRN-020) | ☐ | ☐ | |
| 15 | Select Offline HQ again | | ☐ | ☐ | |

## J-TRN-12. Paid chapter fill and switching engines (P1, C; tablet; DeepL, Google Cloud, Azure keys, Azure in `westeurope`)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | DeepL; chapter 3 top; picker → "Translate the rest of this chapter" | picker closes; chapter fills | ☐ | ☐ | |
| 2 | Show translation off; reopen the picker | the fill is not offered while hidden (TRN-010) | ☐ | ☐ | |
| 3 | Scroll to a paragraph near the chapter's end during the fill | visible paragraphs translate first (TRN-006) | ☐ | ☐ | |
| 4 | Rotate during the fill | no empty flash; fill finishes once (TRN-008) | ☐ | ☐ | |
| 5 | Scroll the whole chapter | each paragraph once; usage +1 per paragraph, not 2 (TRN-007) | ☐ | ☐ | |
| 6 | Google Cloud; next chapter first screen | Google's usage grows; DeepL's does not (ONL-019) | ☐ | ☐ | |
| 7 | Azure with Region `westeurope`; translate a screen | succeeds, no region-mismatch (ONL-012) | ☐ | ☐ | |
| 8 | Back to DeepL; translate; picker | DeepL grows; Google does not (ONL-019) | ☐ | ☐ | |
| 9 | Quick Translate; long-press 30+ words quickly | some wait (backoff), all resolve; never "quota exceeded" for the temporary limit (ONL-004) | ☐ | ☐ | |
| 10 | Offline HQ | restored | ☐ | ☐ | |

## J-TRN-13. Reading on a bad connection (P1, C; real arm64 phone or tablet; DeepL key; EN→RU model and pack installed, EN→DE not)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Target German; Wi-Fi off at ~40 %; Wi-Fi on, Retry | fails with Retry, no hang; resumes near where it stopped (OHQ-013) | ☐ | ☐ | |
| 2 | Target French; force-stop at ~30 %; relaunch, open book | next attempt resumes and completes (OHQ-015) | ☐ | ☐ | |
| 3 | Aeroplane mode; target Italian | "Couldn't download the Offline HQ language pack" (OHQ-019) | ☐ | ☐ | |
| 4 | Offline, reopen book twice, watch 2 min | no retry loop; error with Retry stays (OHQ-018) | ☐ | ☐ | |
| 5 | Offline; target Russian; scroll | Offline HQ translates fully, no banner (OHQ-026) | ☐ | ☐ | |
| 6 | ML Kit; scroll | translates offline; banner may read "On-device translation · no cloud calls" (MLK-011) | ☐ | ☐ | |
| 7 | Online; DeepL; cut network ~2 s mid-page | only the affected paragraph retranslates (TRN-016) | ☐ | ☐ | |
| 8 | Aeroplane; new page; long-press a word | "TRANSLATION FAILED" banner with Retry (TRN-017); popup short muted (not italic) error (POP-016) | ☐ | ☐ | |
| 9 | Network on; Retry | every paragraph on screen translated (TRN-017) | ☐ | ☐ | |
| 10 | 2 s outage on a new screen; restore; scroll on without Retry | banner clears; next screen translates (TRN-018) | ☐ | ☐ | |
| 11 | Popup during a 2 s outage; restore; scroll; same word | translates; old error not shown (POP-018) | ☐ | ☐ | |
| 12 | Chapter 3 fill; cut network while off-screen ~5 s | banner idle; later scrolling fills skipped paragraphs (TRN-019) | ☐ | ☐ | |
| 13 | Offline HQ | restored | ☐ | ☐ | |

## J-TRN-14. Broken packs, full storage, stalled server (P1, C; debug build on an arm64 AVD with run-as; host proxy)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Target German; proxy returns corrupt bytes; long-press a word AND scroll | one shared failure reported once at the end; no retry storm (OHQ-014) | ☐ | ☐ | |
| 2 | Corrupt the installed model file, kill, reopen, translate | SHA mismatch deletes files; one automatic retry; works (OHQ-016) | ☐ | ☐ | |
| 3 | Same with corrupt bytes for the retry | "The Offline HQ pack was corrupted and could not be re-downloaded." (OHQ-016) | ☐ | ☐ | |
| 4 | Proxy good; Retry | downloads; German appears (OHQ-016) | ☐ | ☐ | |
| 5 | Storage below 2× the next pack; target French | no request reaches the proxy; "Not enough storage for the Offline HQ pack (needs … MB free)." (OHQ-017) | ☐ | ☐ | |
| 6 | Free the space; Retry | download starts; French appears (OHQ-017) | ☐ | ☐ | |
| 7 | Proxy accepts and sends nothing; target Italian; wait 90+ s | aborted: "Couldn't download the Offline HQ language pack (…MB). Check your internet and retry." with Retry (OHQ-020) | ☐ | ☐ | |
| 8 | Proxy passes; Retry | Italian appears (OHQ-020) | ☐ | ☐ | |
| 9 | Debug flag: native library fails to load; translate | quiet strip "Offline HQ isn't available on this device — translated by ML Kit"; no crash (OHQ-023) | ☐ | ☐ | |
| 10 | Break the native session mid-use; translate; Retry | recovers; no crash (OHQ-023) | ☐ | ☐ | |
| 11 | Remove proxy and debug flags | | ☐ | ☐ | |

## J-TRN-15. Upgrades, restores, 32-bit devices, security audit (P1, C; phones A and B, 32-bit AVD, Keystore-failing profile, Play Console; v22 and current builds)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | Phone A: v22 with ML Kit chosen explicitly; update via Play to current; relaunch. Repeat with DeepL chosen | choice kept; nothing switches to Offline HQ (OHQ-002) | ☐ | ☐ | |
| 2 | 32-bit AVD, universal debug APK, no translator chosen, new language | ML Kit is the default (OHQ-003) | ☐ | ☐ | |
| 3 | Same AVD: Settings → Translation → Change | Offline HQ not offered; if forced, "Offline HQ isn't available on this device" (OHQ-021) | ☐ | ☐ | |
| 4 | Play Console device catalog, armeabi-v7a-only devices | excluded ("not compatible"); documented, not a bug (OHQ-022) | ☐ | ☐ | |
| 5 | Phone A: DeepL + key; `bmgr backupnow`; inspect | `translator_keys_enc` absent; no plaintext key (SET-017) | ☐ | ☐ | |
| 6 | Phone B: restore A's backup; open a book | DeepL provider, key not restored; banner "DeepL API key isn't set — tap Switch translator to add it" (ONL-008, ONL-001) | ☐ | ☐ | |
| 7 | Long-press a word, Save | TRANSLATION_FAILED toast (or equivalent); no blank Words entry (POP-012, WRD-006) | ☐ | ☐ | |
| 8 | Switch translator → DeepL → key → Save | dialog closes; translation proceeds (ONL-008) | ☐ | ☐ | |
| 9 | Keystore-failing profile: enter key; cold-restart ×3 | each cold start: readable "no key" banner (ONL-007) | ☐ | ☐ | |
| 10 | jadx/apkanalyzer on the `.aab`/APK and the source: keys, `http://`, logging interceptor | no secrets; HTTPS; interceptor debug only (ONL-022) | ☐ | ☐ | |

## J-TRN-16. Real-device feel (P1 when run, C; real arm64 phone + tablet; ML Kit EN→ES and Offline HQ EN→PT not installed)

| # | Do | Expect | P | F | Notes |
|---|---|---|---|---|---|
| 1 | ML Kit, target Spanish, untranslated chapter, portrait | "Downloading model" indicator (MLK-004); known: only "Translating…" up to ~90 s (MLK-005) | ☐ | ☐ | |
| 2 | Leave the screen alone while shimmers become text | bottom panel stays put (MLK-008) | ☐ | ☐ | |
| 3 | Offline HQ, Portuguese, long-press a word | "Downloading language model… N%", then Ready (POP-015) | ☐ | ☐ | |
| 4 | Fresh API 30 AVD: ML Kit needing a model | known: may silently fail; confirm on the real device (MLK-009) | ☐ | ☐ | |
| 5 | Target Russian; drag a handle, pull the shade mid-drag | popup not stuck on "Release to translate" (POP-008) | ☐ | ☐ | |
| 6 | Popup open; tap an illustration, margin, blank gap | known: popup closes only on a tap in text (POP-026) | ☐ | ☐ | |
| 7 | Fling back and forth for 10-15 s; stop | no duplicated or garbled text; right paragraph; banner idle (TRN-023) | ☐ | ☐ | |
| 8 | Settings: Speech rate / pitch non-default; target language separately; restart | separate controls; values kept (SET-015) | ☐ | ☐ | |
| 9 | `mixed-lang.epub`: German word → Listen | German voice at the adjusted rate (TRN-025, SET-015) | ☐ | ☐ | |
| 10 | Long Latin epigraph; one-line Latin fragment | "LA · Latin, not translated"; fragment (< 60 chars) treated as English by design (LNG-007) | ☐ | ☐ | |
| 11 | `no-book.epub` (Norwegian) | known: detected "DA"; no crash (LNG-008) | ☐ | ☐ | |
| 12 | Target German, `ru-book.epub`, Offline HQ | RU→EN then EN→DE ensured with progress across both legs; German reader judges fluent (OHQ-006) | ☐ | ☐ | |

---

# Part C. Manual halves of automated journeys

The automated journey passes without these. Look at them on the same run as the journey (tablet and phone as
noted). Cases in brackets. Source: the "MANUAL" notes inside `docs/e2e/e2e_journeys.md`.

### C-01. Visual and not-observable checkpoints (P1, C)

| # | Journey | Check | Expect | P | F | Notes |
|---|---|---|---|---|---|---|
| 1 | J-LIB-08 | A book whose every paragraph is under 40 characters as most recent (no fixture exists; build one) | hero shows an empty teaser without a placeholder or crash (LIB-019) | ☐ | ☐ | |
| 2 | J-LIB-08 | Search field with the keyboard up | no blank band above the keyboard (LIB-011) | ☐ | ☐ | |
| 3 | J-LIB-08 | Last opened hero | keeps its widened size; progress row pinned | ☐ | ☐ | |
| 4 | J-LIB-08b | Phone 360dp: filter pills row | shelf header never grows tall; a pill is cut off at the right edge before the swipe (LIB-014, LIB-015) | ☐ | ☐ | |
| 5 | J-LIB-08b | Phone 360dp: hero eyebrow "CONTINUE READING" | known finding: wraps to two lines in the narrow title column | ☐ | ☐ | |
| 6 | J-RDR-01 | Scroll a picture or chapter title under the divider handle (tablet) | the handle fades out instead of covering it (ILL-008) | ☐ | ☐ | |
| 7 | J-RDR-05 | Settings Theme row after each theme change; Display dialog after Sepia / Paper | the right swatch shows as selected (border only) (APP-007) | ☐ | ☐ | |
| 8 | J-RDR-06 | Drag each slider | live change, no slider jump; justified text looks right; nothing clips; ILL-005 pictures flush with the text | ☐ | ☐ | |
| 9 | J-RDR-07 | Font scale 1.3: "EN → RU" chip and top bar | chip text drawn uncut | ☐ | ☐ | |
| 10 | J-RDR-07 | Chapter picker jump to chapter 1 (several times) | lands on the masthead, not pictures above the first text (known intermittent; see Task 10 report) | ☐ | ☐ | |
| 11 | J-RDR-08 | Bookmark jump while translations arrive | see M-05 | ☐ | ☐ | |
| 12 | J-TRN-01 | Open a chapter whose rows translate | no visible "Translating" delay on screens covered by the prefetch (TRN-005); a cache hit shows no placeholder | ☐ | ☐ | |
| 13 | J-TRN-02 | Long-press a word | "Translating…" line lasts a fraction of a second, plain styling (POP-016, POP-002) | ☐ | ☐ | |
| 14 | J-TRN-02 | Touch and drag the end handle | muted "Release to translate", no spinner or result while the finger is down (POP-003) | ☐ | ☐ | |
| 15 | J-TRN-02 | Drag out two words, back one, out three, back two, release | exactly ONE request for the final selection, no flicker (POP-004, POP-005); drag into the margin: one request (POP-006); tap the handle / press it still 0.7 s: no new request (POP-007). Observe via logcat or a proxy | ☐ | ☐ | |
| 16 | J-TRN-02 | Long-press a punctuation-only token (em dash) | no popup opens (POP-014; not reachable with `en-novel.epub`, use any book with an isolated dash) | ☐ | ☐ | |
| 17 | J-TRN-03 (step 13) | "Download and translate" for a paragraph in another language | "while Translating, Save does nothing" (POP-010) | ☐ | ☐ | |
| 18 | J-TRN-07 | Scroll with an invalid key banner | scrolling sends no new request (ONL-002) | ☐ | ☐ | |
| 19 | J-TRN-07 | Invalid-key popup | muted, not italic (POP-017); no flicker when re-picking the current engine (SET-007) | ☐ | ☐ | |
| 20 | J-TRN-08 | Azure keyless Save | no toast appears (POP-012; a toast absence cannot be asserted) | ☐ | ☐ | |
| 21 | J-TRN-08 | Quick Translate: Show translation off, scroll three screens | shimmer placeholders look right (ONL-014) | ☐ | ☐ | |
| 22 | J-TRN-08 | Update from v28 with LibreTranslate selected (needs a v28 install) | one-time notice "LibreTranslate is no longer supported — switched to Offline HQ."; shown once (ONL-024) | ☐ | ☐ | |
| 23 | J-TRN-09 | Device where Bergamot cannot run | Offline HQ's own strip "Offline HQ isn't available on this device — translated by ML Kit" (see J-TRN-14 steps 9-10) | ☐ | ☐ | |
| 24 | J-TRN-04 | Paragraph in another language | popup translates "from" that language, not the book's (POP-024); Language packs lists five packs | ☐ | ☐ | |
| 25 | J-TRN-10 | Fast fling of a huge book to a landing | at most ~8 paragraphs processed before the landing (TRN-022) | ☐ | ☐ | |
| 26 | J-LIB-04 | `pictures-only.epub`: the two pictures | red first, then blue, one under the other (PRS-005 "in order"; the 1×1-dp pictures are blended at their edges, so a crop cannot tell) | ☐ | ☐ | |
| 27 | J-LIB-04 | Alice from the start (screenshot `J-LIB-04-step2-alice-start`) and every chapter | the cover shows only on the card, never as a page; no gap where the cover's SVG wrapper was skipped (IMP-002, PRS-003) | ☐ | ☐ | |
| 28 | J-LIB-07 | Words search field with the keyboard up (screenshot `J-LIB-07-step7-words-search-keyboard`) | no blank band between the list and the keyboard (WRD-004) | ☐ | ☐ | |
| 29 | J-LIB-07 | A selection that normalizes to nothing, e.g. drag a selection over a lone "—" and Save | "Nothing to save", no entry (WRD-014; a long press on "—" opens no popup, which the flow checks) | ☐ | ☐ | |
| 30 | J-LIB-07 | A paid engine (DeepL) chosen with its key field left empty, Save a word | not possible from a clean install (choosing DeepL asks for its key); the keyless Save is J-TRN-08 step 8 (Azure) (WRD-006) | ☐ | ☐ | |

---

## Placement notes

- All 26 manual journeys of the catalogue (J-LIB-09, J-LIB-11..18, J-RDR-10..20, J-TRN-11..16) have a section.
- J-LIB-09: only steps 1-3, 6, 12 have an automated screens-only twin (J-LIB-09a).
- The release smoke R-01..R-06 is automated; its "not covered" items are M-01 (DeepL, Google, Azure DTOs),
  M-02 (Gutenberg), J-LIB-11/12 (real purchase) and M-04 (Crashlytics).
- Known bugs (IMP-011, MLK-005, MLK-009, POP-026, LNG-008, BMK-011..014, ILL-010, SYS-011) are recorded, never blocking.
