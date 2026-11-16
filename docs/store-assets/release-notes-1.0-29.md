# Release notes — Mirrolit 1.0 (versionCode 29), closed testing

## Play Console → "What's new" (en-US)

```
Selecting text is steadier: tapping or holding a selection handle keeps your selection and its translation. On phones, all Catalog sources — including Google Drive — are now visible. The language picker shows whether the ML Kit model is downloaded. Fixes: clearing a LibreTranslate setup, the cached-translation count in Settings.
```

## Note for testers

**Mirrolit 1.0 (29) — what to try**

1. Long-press a word, then tap one of the selection handles, or hold it still for a second: the
   selection and its translation stay. Dragging a handle still extends the selection and translates it once.
2. On a phone, open the Catalog: every source pill, Google Drive too, is on screen (on a second line if needed).
3. Open the language picker ("To" tab): the strip under the languages says "ML KIT · DOWNLOADED" or
   "NOT DOWNLOADED" for the book's pair — opening it downloads nothing.
4. LibreTranslate with your own server and no key: the card shows "SERVER · your-host". Clear in the
   key dialog resets the server and key; the card goes back to "Tap to add API key".
5. Read for a while, then open Settings: "Cached translations" shows the new number without a restart.
