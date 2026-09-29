# Reading position is stored in paragraph coordinates, not list items

The reader used to persist the top visible LazyColumn item index as "progress" and "bookmark". That number depends on the screen's layout (chapter mastheads, whether illustrations are shown), and the code read it as a paragraph index anyway, which shipped wrong bookmarks and stats. We store every Reading position (progress, bookmarks, excerpt, session stats) as (chapter, paragraph, pixel offset). Only the reader screen converts between list items and paragraphs, through one index. We accept that restoring to the exact pixel inside a masthead or illustration is no longer possible: the position snaps to the first paragraph at least partly visible.

## Consequences

- Positions saved before this change were in item coordinates. A one-off migration converts them per book when the book is first opened, using a frozen copy of the old layout arithmetic. It does not use the live screen index, because that index may change later.
- Progress is one position per book. The old per-chapter scroll keys were only ever read for the last chapter.
