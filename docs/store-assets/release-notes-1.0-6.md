# Release notes — Mirrolit 1.0 (versionCode 6), internal testing

## Play Console → "What's new" (en-US, 486 chars)

```
• The word popup now shows its state: download progress, "Translating…", the translation or a short error. Save keeps the translation you see, with no second paid request.
• A missing or invalid API key shows a clear error instead of silently switching to ML Kit. A strip names the engine when another one translated.
• Page margins in Settings now work (4–48 dp).
• Books reopen at the exact paragraph; bookmarks are more precise.
• A hidden translation pane no longer uses paid quota.
```

## Note for internal testers

**Mirrolit 1.0 (6) — what to try**

This build reworks the reader internals (reading position, translation engine, translator settings, word
translation, appearance settings). It should look the same, with these visible changes:

1. Long-press a word: the popup shows "Translating…", then the translation. Drag a selection handle: it
   shows "Release to translate" and translates once when you let go. Save stores exactly what the popup shows.
2. Choose DeepL with a wrong key: a clear "Invalid DeepL API key" error with Retry / Switch translator,
   instead of ML Kit quietly translating. Choose a language DeepL doesn't support (Hindi): a strip says
   "translated by ML Kit".
3. Settings → Page margins now changes the reader's side margins (default 32 dp = the old look). If you
   moved this slider before, your old value now takes effect; set 32 to get the previous look back.
4. Books and bookmarks from build 5 open at the same paragraph (converted once, on first open).
5. Hide the translation pane on a paid provider: the monthly counter stops growing until you show it again.
