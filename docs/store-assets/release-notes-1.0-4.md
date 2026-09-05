# Release notes — Mirrolit 1.0 (versionCode 4), internal testing

## Play Console → "What's new" (en-US, short — 203 chars)

```
New: Offline HQ — a higher-quality offline translator (Mozilla models). Pick it in Settings → Translation; a ~30 MB pack downloads once per language.
Fixes: on-screen translation accuracy, stalled pages, rate-limit retries.
```

## Longer "What's new" variant (458/500 chars)

```
New: Offline HQ — a higher-quality on-device translator built on Mozilla's open translation models. Pick it in Settings → Translation; a ~30 MB language pack downloads on first use, then everything runs offline. Non-English pairs translate via English. Manage downloaded packs in Settings → Language packs.

Fixes: the reader now translates the paragraphs actually on screen; one failed request no longer stalls the rest of the page; temporary rate limits are retried automatically.
```

## Longer note for internal testers (email / tester group message)

**Mirrolit 1.0 (4) — what to try**

1. **Offline HQ translator.** Settings → Translation → Change → *Offline HQ*. Open any English book with Russian (or another) target. You should see "Preparing translation… NN%" while the pack downloads (17–44 MB per direction), then translations in the right pane. Switching back to ML Kit and again to Offline HQ must not re-download.
2. **Pairs without English** (e.g. a Russian book → German) go through English and need two packs; expect a slower first run.
3. **Settings → Language packs** lists what is installed with sizes and a Delete button.
4. **Reader fixes:** translation follows the paragraphs on screen after fast scrolling; a single failed request no longer leaves the rest of the window untranslated; Quick Translate rate limits retry with backoff instead of showing "quota exceeded".

**Known limits in this build**
- Offline HQ needs a 64-bit device (arm64); on 32-bit-only phones the app is not offered.
- Each loaded model uses ~200 MB of RAM; on 4 GB phones prefer pairs that include English.
- Very long single sentences translate noticeably slower (~0.5 s) than ordinary paragraphs.

**Please report**: any "Couldn't download the Offline HQ language pack" or "Offline HQ isn't available on this device" message, with phone model and whether you were on Wi-Fi.
