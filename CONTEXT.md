# Mirrolit

A multi-language e-book reader that shows a book's original text alongside a live translation.

## Language

### Reading

**Paragraph**:
One entry of a chapter's text, numbered from 0 within its chapter. Chapter mastheads and illustrations are not paragraphs.
_Avoid_: item, line, block

**Reading position**:
Where the reader is in a book: a chapter, a paragraph within it, and a pixel offset into that paragraph. It names the first paragraph at least partly visible at the top of the screen; everything the app remembers about "where you are" (progress, bookmarks, the continue-reading excerpt) is a Reading position.
_Avoid_: scroll position, item index, anchor, current paragraph

**Bookmark**:
A Reading position the reader saved on purpose. It always opens at the start of its paragraph.
_Avoid_: mark, saved position
