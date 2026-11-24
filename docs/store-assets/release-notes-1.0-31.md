# Release notes — Mirrolit 1.0 (versionCode 31), closed testing

## Play Console → "What's new" (en-US)

```
Open Library keeps working when its main site is down: popular books, search and downloads switch to a backup catalog and mirror automatically. Catalog errors now show above the list (with Retry for a failed search) instead of disappearing, and a failed search no longer shows the old list as its results.
```

## Note for testers

**Mirrolit 1.0 (31) — what to try**

1. Open the Catalog → Open Library tab. While the main book site is down for maintenance, POPULAR
   still fills in, and tapping a book downloads and opens it. Downloads from the backup mirror can be
   slow: a large illustrated book may take several minutes. Small books take seconds.
2. Search in Open Library. The backup search is sometimes slow or does not answer; you then see
   "This catalog is temporarily unavailable. Try again later." above the list, with Retry. The list
   under it keeps the label of what it shows (POPULAR stays POPULAR).
3. Turn on airplane mode and search or download: you see the network error in the same banner above
   the list; Dismiss hides it.
4. Standard Ebooks and Google Drive work as before.
