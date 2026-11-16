# Release notes — Mirrolit 1.0 (versionCode 29), closed testing

## Play Console → "What's new" (en-US)

```
From now on, a book you delete and add again from another folder (Recent, Downloads…) comes back with your place, bookmarks and notes, and picking a library book from another folder doesn't make a second card. LibreTranslate is no longer offered: if you used it, you're switched to Offline HQ (or ML Kit) and told once. Selecting text is steadier, all Catalog sources show on phones, and the language picker shows your offline model's state and where online translators send text.
```

## Note for testers

**Mirrolit 1.0 (29) — what to try**

1. Long-press a word, then tap one of the selection handles, or hold it still for a second: the
   selection and its translation stay. Dragging a handle still extends the selection and translates it once.
2. On a phone, open the Catalog: every source pill, Google Drive too, is on screen (on a second line if needed).
3. Open the language picker ("To" tab): the strip under the languages names your translator and its
   model for the book's pair — "OFFLINE HQ · DOWNLOADED" / "NOT DOWNLOADED" (or "ML KIT · …" with
   ML Kit). With an online translator there is no model line, and the strip says
   "Online translation · text is sent to …" (DeepL, Google, …) instead of "no cloud calls".
   Opening it downloads nothing.
4. Read for a while, then open Settings: "Cached translations" shows the new number without a restart.
5. Same book from another folder. It works for books added in this version, or opened from the Library
   in this version (that gives them a fingerprint). Books deleted before this version are not recognised.
   a. Open a book from the file picker's **Recent**, read a few pages, add a bookmark and a note,
      save a word. Delete it from the Library, keeping the reading data (the default).
   b. Add the same file again from **Downloads** (or the folder it is in). It opens where you stopped,
      with its bookmark and note; Words and the Almanac still show it. On the free tier this needs a
      free slot, as any re-added book does.
   c. With the book in the Library, add the same file again from the other folder: still one card,
      same place. On the free tier with 3 books this works too (no upgrade dialog).
   d. A different file is still a new book.
6. LibreTranslate is gone: it is no longer in the translator list. Update from version 28 with
   LibreTranslate selected: the first book you open says once "LibreTranslate is no longer supported —
   switched to Offline HQ." (ML Kit on 32-bit devices). After OK it does not come back, the reader
   translates with the new engine, and the LibreTranslate key and server are gone. If you pick another
   translator in Settings before opening a book, no notice appears. Users of any other translator
   see nothing.
