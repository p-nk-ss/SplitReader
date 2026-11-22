# Release notes — Mirrolit 1.0 (versionCode 30), closed testing

## Play Console → "What's new" (en-US)

```
Chapters without a heading of their own are now listed as "Chapter N" instead of a blank line. In FB2 books, the book's opening epigraph and its author now come first in chapter 1. When a Catalog source is down for maintenance, it says so ("This catalog is temporarily unavailable. Try again later.") instead of "HTTP 503". A search replaced by a newer one no longer flashes an error.
```

## Note for testers

**Mirrolit 1.0 (30) — what to try**

1. Open an EPUB whose first pages come before the first chapter heading (a title page, a dedication),
   or a picture book with no text. Open the chapter list: that first entry reads "Chapter 1" (and the
   next ones "Chapter 2", …), never an empty line. Chapters and your saved place are the same as before.
2. Open an FB2 book that starts with an epigraph before the first chapter. Chapter 1 begins with that
   epigraph and its author line, then the chapter's own epigraph, then the text.
   Known for such books already in your Library: in chapter 1 only, your place, bookmarks, notes and
   highlights may sit one paragraph off (the added author line moves the text down). Other chapters
   and other books are not affected.
3. Catalog: when Open Library or Standard Ebooks is down, searching or downloading a book shows
   "This catalog is temporarily unavailable. Try again later." With no internet you still see the
   network error, as before.
4. Catalog: type a search, then quickly change it. Only the results of the last search appear, with
   no error in between.
