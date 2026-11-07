# Fixture books — sources, hashes, and journey coverage

Built by `build_fixtures.py` (synthetic, committed — total ≈ 87.5 KB, well under the 200 KB
budget) and `fetch_fixtures.sh` (real Gutenberg books, downloaded on demand into `fetched/`,
**gitignored, never committed**). Two large pathological fixtures (`zipbomb.epub`, `oversized.fb2`)
are also generated locally, never committed — see the `--large` flag and the note under "Large,
not-committed fixtures" at the end of this file.

Symbolic fixture names (`F-ALICE`, `F-LONG`, …) come from `docs/e2e/e2e_journeys.md`'s three
merged sections (Library/Import, Reader, Translation); the same underlying book is often reused
under several names since the three sections were written independently. The **Journeys** column
below lists every journey (automated or manual) that names the file, by its literal filename or
its symbolic fixture code.

Regenerate with:
```
/opt/homebrew/opt/python@3.12/libexec/bin/python3 e2e/books/build_fixtures.py --out e2e/books
e2e/books/fetch_fixtures.sh
```

## Synthetic fixtures (committed)

All built by `build_fixtures.py`. Licence: own text (public-domain quotes/own-written sentences,
no copyrighted prose); kind = synthetic. "First paragraph" is the fixture's actual opening text
(or the first distinguishing line for structural MOBI fixtures where exact wording isn't the
point).

| File | Size | sha256 | dc:title / book title | First paragraph (~60 chars) | Journeys |
|---|---|---|---|---|---|
| `cover-b.epub` | 1923 | `137f6490100090fa670b7263491bbc8955af539f264952ca12f5d65964e88c92` | The Blue Cover Book | "The old mill still stood at the bend of the river…" | F-COVER-B (J-LIB-04) |
| `image-chapter.epub` | 2167 | `393ac1c05468bbade55db1c90f9eff539324157854b66643059b73cf9039cc1d` | Image Chapter Book | (ch1 is image-only) ch2: "The old mill still stood…" | F-IMGCH/F-IMGFILES (J-LIB-04, J-RDR-08/13) |
| `pictures-only.epub` | 1703 | `c8fc529e3c7f5bdc7853be85445577ad9e9c3bae88cb3f5621e910dbcef8c321` | Pictures Only | (no text; two images, no `dc:language`) | F-PICS/F-IMGONLY (J-LIB-04, J-RDR-08, J-TRN-10) |
| `no-description.epub` | 2560 | `c510d2c9ed95ccee1403e2ad5f505dc0c6fd053c3a21aa647281b7830e27d987` | No Description Here | "The old mill still stood at the bend of the river…" | F-NODESC (J-LIB-01, J-LIB-08) |
| `tiny.epub` | 1578 | `5a4844c2350426897edf58092a7501bcf637e729a26f0ca2d683cd0569bc3a91` | Tiny Book | "A very short first line." (24 chars) | F-TINY (J-LIB-04, J-LIB-08) |
| `english-with-french.epub` | 2734 | `aa89e5bea460fcaed41a3da51e8994bae7072a575323a9fe0e8ca493f453bc9d` | An English Novel | "The old mill still stood at the bend of the river…" | F-FOREIGN (J-LIB-10) |
| `mixed-lang.epub` | 2495 | `4db5e574cb18277f85513d9ecf229feaabe6e17b68ec61a7c0d09f89a916940d` | Letters in Many Tongues | "It was late in the autumn when the letter finally…" | `mixed-lang.epub` (J-TRN-04, J-TRN-10, J-LIB-10, J-RDR-??) |
| `letter-prague.epub` | 2272 | `e8780b63471d7bd068bd16eef57018ae7ab3f1d5fa5348b5de94b3702b24c0c5` | A Letter from Prague | "It was late in the autumn when the letter finally…" | Czech-paragraph book (brief) |
| `fi-book.epub` | 2584 | `853965ca61a41f8eaf239ff453beb7b40a36233226c30b06f24914a839c31381` | Syksyn ilta Helsingissä | "Syksyinen sumu lepäsi pitkään kaupungin yllä…" | `fi-book.epub` (J-TRN-06) |
| `de-book.epub` | 2558 | `fa0944e7180641dcb4162aaab442a4c22feb221b11905e1fa0715a90738cd632` | Ein Herbstabend in Berlin | "An jenem Abend hing lange ein warmer Nebel…" | `de-book.epub` (J-TRN-06) |
| `es-book.epub` | 2558 | `1b08f3bbab3c3db87d69727e4320f91e10f20ce828803104625687e57210de3d` | Una tarde de otoño en Madrid | "Aquella tarde una niebla cálida permaneció…" | `es-book.epub` (J-TRN-06) |
| `ru-book.epub` | 2832 | `fea3ee03a08f711c81790d14c18a05ae35d56f90c5fdb76a60498179f7504ce7` | Осенний вечер в Москве | "В тот вечер над городом долго висел тёплый…" | `ru-book.epub` (J-TRN-09, J-TRN-10, J-TRN-16, J-LIB-10) |
| `no-book.epub` | 2472 | `68155d5bac719a0722fd41bc46ef775cb956699a5435568133ed1696adc62743` | En høstkveld i Oslo | "Den kvelden lå en varm tåke lenge over byen…" | `no-book.epub` (J-TRN-16; known-bug LNG-008, detected as Danish) |
| `und-book.epub` | 2088 | `7f0720acabecbac6d51c17985feb1c7fe72ce9226ddba7baca46c1ebd4ae22e3` | Undetectable Text Sample | "1234567890 -- 2026/11/07 §¶ 42 100% 3.14159…" | `und-book.epub` (J-TRN-06, J-TRN-16) |
| `ja-poems.epub` | 2241 | `0dab9a48903201000e7ba7ce55b8ee3567955c70c97001daeb223dfa6628837b` | Short Autumn Poems | preface: "This collection gathers a handful of short…" | `ja-poems.epub` (J-TRN-05) |
| `ja-novel.epub` | 2561 | `73c70fed880337c9f748a3fb14bdffe0e72c52cb3a3c3cc82e4bd416ade69a94` | 長い章のテスト小説 | "その夜、町の上には長いあいだ暖かい霧が垂れ込めていて…" | `ja-novel.epub` (J-TRN-05, J-TRN-16) |
| `zh-novel.epub` | 2471 | `2d9b3786fc789b8e648f778fedde39cf88625803caa2681520d23b4f3bc23c5b` | 长章节测试小说 | "那天晚上，温暖的雾气在城市上空久久不散…" | `zh-novel.epub` (J-TRN-05, J-TRN-06) |
| `en-novel.epub` | 18304 | `0bceaefcc69ad3f82a6a9e00220269e78190fe66a1789e5a7347a09336dc825e` | The River Crossing | "The old mill still stood at the bend of the river…" | `en-novel.epub` / **F-LONG** (J-TRN-01/02/03/07/09/10, J-RDR-02/03/04/05/08/09/16/17) |
| `short-novel.epub` | 2023 | `c139dd83edfbe00776dbb7edab3af3c05ef1eac2344e4b7cb59073c549f0da3d` | A Short Crossing | "The old mill still stood at the bend of the river…" | **F-SHORT** (reader) (J-RDR-04) |
| `plain-prose.epub` | 4329 | `9c178312305d994e3d9db61f32e4728ba813e3331f8939c7722d59d19c64db5a` | Plain Prose | "The old mill still stood at the bend of the river…" | F-PROSE (J-LIB-04) |
| `long-chapter.epub` | 3693 | `b06fa8eb3a3f0b0bd76484ffa1c8ad6aa55be1a396dca27956672ab38cea9fef` | One Very Long Chapter | "The old mill still stood at the bend of the river…" | `long-chapter.epub` (J-TRN-10) |
| `empty.epub` / `empty-book.epub` | 1401 (each) | `5d9d0b6bf420d21f41ac351a58072117c3d734c391ee6fc63b4486e651a0edb0` | Empty Book | (no extractable content) | F-EMPTY / F-EMPTY1 / `empty-book.epub` (J-LIB-03, J-RDR-15, J-TRN-10) |
| `empty-first-chapter.epub` / `empty-chapter.epub` | 1961 (each) | `749621b8cf132631a825612866a644afe7a0a29a273552458c239dc865c50eb8` | Empty First Chapter | (ch1: heading only, 0 paragraphs; ch2 has text) | F-EMPTY-CH1 / `empty-chapter.epub` (J-LIB-18, J-TRN-10) |
| `short-header.epub` | 4 | `8dcc7e601606217f3b754766511182a916b17e9a26a94c9d887104eba92e9bb2` | — | n/a (4 bytes, shorter than the header-sniff length) | F-SHORT (library/ERR-008) (J-LIB-03) |
| `corrupt.epub` | 68 | `7872b98743fa0c180b527523f73538a88121f2e30b122eb4f9f85e46d3b74ea8` | — | n/a (truncated/broken zip) | F-CORRUPT-EPUB (J-LIB-03) |
| `full-features.fb2` | 9122 | `471cd6bf9da3e4624b4a59d5116a54c3858694f3274d7f68d93962aca9e2bf09` | The Complete Letters | "The letter arrived on a grey morning, long after…" | F-FB2 (J-LIB-01/05, J-RDR-08) |
| `no-chapters.fb2` | 487 | `c61f3d52e7ffcfc0d3f789bdafab9fd72fa9e4db3752b8c8a89c58123892e688` | No Qualifying Chapters | (sections exist, none has a paragraph) | F-FB2-NOCH (J-LIB-03) |
| `russian-1251.fb2` | 611 | `f4cc6881583da3f367a9b5a4accb85dc08d95245717e4d7e14418dfce0d5de4b` | Ветер над рекой | "В тот вечер над городом долго висел тёплый туман…" | F-FB2-1251 (J-LIB-05) |
| `corrupt.fb2` | 120 | `281175d43e0e900b45073774e03b65af01cfceb27c16ae4205898a95e98905a5` | — | n/a (cut off mid-tag, no closing tags) | F-CORRUPT-FB2 (J-LIB-03) |
| `classic-h2.mobi` | 1078 | `23d7bb811b828a0c57e4cc0f64b57ae0338091fad728bae88e99be0cbff55001` | Classic H2 Test | "Chapter 1 opens here, on a bright morning by the old mill…" | F-MOBI (J-LIB-01/06, J-RDR-07) |
| `pagebreaks.mobi` | 737 | `097fbee829bfba3e0ef0344894c8c22e311c53b608bf29405392e9d3259ce374` | Pagebreaks Test | "Section 1: the ferry crossed at dawn…" | F-MOBI-PB (J-LIB-06) |
| `pagebreaks-and-h2.mobi` | 592 | `0c555fae49a97f67daee20c3c260413739e6f1188177f5d42d2794da1e9e6a72` | PB+H2 Test | "Inner Heading 1 / Region 1 begins with its own h2…" | F-MOBI-PBH (J-LIB-06) |
| `h1-h2.mobi` | 613 | `8ec2a5f4c38eaa7ae4e742bfd7b2e4bccb292f3cc9397f27ba388d929881822f` | H1 H2 Test | "Book 1 / Section 1.1 / The first section of book 1…" | F-MOBI-H1H2 (J-LIB-06) |
| `plain.mobi` | 388 | `a883eb17f740bd39b219cfd43a2ec1018ea711d63f0d4aa046a270786c5ec327` | Plain MOBI Test | "There are no headings and no page breaks anywhere…" | F-MOBI-PLAIN (J-LIB-06, J-LIB-08) |
| `palmdoc-distance0.mobi` | 487 | `93b658eb68f5e7da44392b9f5ddebc7d601dd99fe6ba6ed4ca27bced18deb31a` | Distance0 Test | "The paragraph just before the crafted token reads…" | F-MOBI-DIST0 (J-LIB-18) |
| `huffcdic.mobi` | 102 | `18961072a9e82c95781871c93e0c717a6eba4aaa49539e19d56bd75d5c5f7403` | — | n/a (HUFF/CDIC compression flag only, rejected pre-decompression) | F-HUFF (J-LIB-03) |
| `drm.mobi` | 102 | `3e4f70ebede3db0e2ef1a442972b02958cedfa858cde5aac027046321546cc11` | — | n/a (encryption flag only, rejected pre-decompression) | F-DRM (J-LIB-03) |
| `corrupt.mobi` | 78 | `6309e52e3a9f5bb6cc325872cf0d84c448ab6d7fb6d2028fb4a74fc1d54eac9b` | — | n/a (PDB header claims 5 records, none exist) | F-CORRUPT-MOBI (J-LIB-03) |
| `kf8.azw3` | 130 | `a60e35cff120b4c2eb2f3c5eebea8cfada157bf7cd6fd870599bc555d78843ed` | — | n/a (MOBI header with fileVersion=8 only) | F-AZW3 (J-LIB-03) |

Notes on the table above:
- `empty.epub`/`empty-book.epub` and `empty-first-chapter.epub`/`empty-chapter.epub` are each
  **byte-identical pairs** (same generator call, written under two filenames) because the three
  journey documents use different literal names for the same concept.
- `palmdoc-distance0.mobi`: the crafted distance-0 backreference pair (bytes `0x80 0x00`, which
  `MobiCodec.palmDocDecompress` must decode as distance=0/length=3 and **skip**, per
  `MobiCodecTest.palmDoc_distanceZero_doesNotCorruptOrHang`) sits in the single text record at
  byte offset `194` (right after `"...nothing unusual in it at all, so a reader would not notice
  anything from the text alone.</p>"` and before `"<p>The second paragraph..."`). Verified by
  decoding the record in Python with a port of the exact Kotlin algorithm (see task report).
- MOBI "First paragraph" cells quote the first distinguishing sentence, not book-length prose —
  these are structural fixtures (classic-h2/pagebreaks/h1-h2/plain/distance0), not readability
  fixtures.

## Fetched fixtures (real Gutenberg books — gitignored, NOT committed)

Downloaded by `e2e/books/fetch_fixtures.sh` into `e2e/books/fetched/`. Hashes below are **as
observed on 2026-11-07** when this task ran; `fetch_fixtures.sh` has them baked in and refuses
(deletes + exits non-zero) on any mismatch. "First paragraph" here is the EPUB's literal first
`<p>` as produced by Gutenberg's ebookmaker — for several of these that is the Gutenberg
**title-page boilerplate** ("Title: …" / "Author: …"), not the narrative's first line; verify
against the real app's rendering in Task 5 (the app's EpubParser/HtmlChapterExtractor may treat
this title page as its own short chapter, which is fine for import/structural tests but means the
"first paragraph" shown here is not necessarily what a reader sees as the hero excerpt).

| File | Size | sha256 (2026-11-07) | Source URL | Licence | dc:title | dc:language | First `<p>` (Gutenberg boilerplate) | Journeys |
|---|---|---|---|---|---|---|---|---|
| `alice-illustrated.epub` | 1,475,456 | `69c757719102f175303e7e8b8e508f1466e9aa78ddd9afb05609d00150699b3d` | `https://www.gutenberg.org/ebooks/28885.epub3.images` | Public domain | Alice's Adventures in Wonderland / Illustrated by Arthur Rackham. With a Proem by Austin Dobson | en | "Title: Alice's Adventures in Wonderland" | **F-ALICE** (J-LIB-01/02/04/09, J-RDR-01/06/08/10/11/13/14/15) |
| `rur-cs.epub` | 118,294 | `4ee88d1ee003543e89c6054c5953b70b16516144f79210bd5ecd60b453910663` | `https://www.gutenberg.org/ebooks/13083.epub3.images` | Public domain | R.U.R. | cs | "Author: Karel Čapek" | **F-RUR** / `rur-cs.epub` (J-LIB-04, J-RDR-08, J-TRN-06) |
| `iliad-el.epub` | 2,060,958 | `3ad87d8902119d6c27e3a1ed0755bfaa858700eb566402a61a5507fe87355957` | `https://www.gutenberg.org/ebooks/36248.epub3.images` | Public domain | Ιλιάδα | el | "Translator: Alexandros Palles" | **F-ILIAD** (J-LIB-04, J-RDR-18) |
| `don-quixote-es.epub` | 1,106,045 | `9a40acbbc8e640fa0fdc51a057ab3905676d404c4ba44090184098d4d8244fc0` | `https://www.gutenberg.org/ebooks/2000.epub3.images` | Public domain | Don Quijote | es | "Title: Don Quijote" | `don-quixote-es.epub` (J-TRN-06) |
| `dream-of-red-chamber-zh.epub` | 1,228,855 | `b387660f0a27441ab6f724b0e3a0da30d4776e90158b9aad9e1f36b518ef3d21` | `https://www.gutenberg.org/ebooks/24264.epub3.images` | Public domain | 紅樓夢 | zh | "Author: Xueqin Cao" | Chinese fixture (brief §24264) |
| `rashomon-ja.epub` | 86,139 | `090f520a4178b3a5575952728e5da718cc5e631de5255130d5ccd6608d11ed94` | `https://www.gutenberg.org/ebooks/1982.epub3.images` | Public domain | 羅生門 | ja | "Author: Ryūnosuke Akutagawa" | `rashomon-ja.epub` (J-TRN-06) |
| `gutenberg-classic.mobi` | 190,274 | `c6de8f83459904177eac2623aa653ef57483ed6968078c8fc3d3171f20e06408` | `https://www.gutenberg.org/ebooks/11.kindle.noimages` | Public domain | Alice's Adventures in Wonderland | en | (PalmDOC, confirmed `compression=2`, `fileVersion=6` — real classic MOBI, not KF8) | Realism/smoke only — the 10 `F-MOBI*` structural variants above cover the parser's branch logic exactly; see `--out` note below |

Also already present (not built by this task, pre-existing, plain/no-illustration Alice):
`app/src/androidTest/assets/qa-pd/gutenberg-11.epub` — Gutenberg id 11, `dc:title` "Alice's
Adventures in Wonderland", cover only (no body plates) — the brief's "plain English novel EPUB".
Not copied into `e2e/books/`; journeys should reference it at that existing path, or it can be
copied in by a later task if Maestro needs it alongside the other fixtures in one directory.

### Why `gutenberg-classic.mobi` is fetched in addition to 10 synthetic MOBI variants

The brief's decision list says to fetch "classic MOBI (Gutenberg .mobi)". The 10 synthetic
`F-MOBI*`/`F-HUFF`/`F-DRM`/`F-AZW3`/`F-CORRUPT-MOBI` fixtures give exact, reproducible control over
every branch `MobiParser`/`MobiCodec`/`MobiChapterSplitter` takes (compression, encryption,
MOBI-header presence, page breaks vs. headings, the distance-0 decompression guard) — verified in
this task by decoding each one in Python with a port of the exact Kotlin algorithm (see the task
report). `gutenberg-classic.mobi` is kept as one additional **real-world** classic MOBI (confirmed
PalmDOC-compressed, MOBI6, no EXTH surprises) for a realism/smoke pass, not for structural coverage.

## Large, not-committed fixtures (`--large` flag only)

Neither is ever committed; both are generated straight into `e2e/books/fetched/` (gitignored):

| File | What | Why not committed |
|---|---|---|
| `zipbomb.epub` (F-ZIPBOMB) | ~320 MB inflated from a ~320 KB DEFLATE container (single repeated byte) | DEFLATE's 32 KB window caps the compression ratio at ~1000:1 even for maximally repetitive input, so a container that inflates past `MAX_DECOMPRESSED` (~300 MB) is itself ~300+ KB on disk — over the whole suite's 200 KB committed budget by itself. |
| `oversized.fb2` (F-HUGE) | ~320 MB of plain (uncompressed) `<p>` padding | A plain-text file "far past a sane book size" is, by construction, that size on disk — there is nothing to compress away. |

Build both with:
```
/opt/homebrew/opt/python@3.12/libexec/bin/python3 e2e/books/build_fixtures.py --large
```

## Determinism proof

`build_fixtures.py --verify` builds the full synthetic set twice, into two separate temp
directories, and diffs sha256 per file. Run 2026-11-07:

```
$ /opt/homebrew/opt/python@3.12/libexec/bin/python3 e2e/books/build_fixtures.py --verify
OK  classic-h2.mobi  23d7bb811b828a0c57e4cc0f64b57ae0338091fad728bae88e99be0cbff55001
OK  corrupt.epub  7872b98743fa0c180b527523f73538a88121f2e30b122eb4f9f85e46d3b74ea8
... (39 files, all OK)
VERIFY: PASS — byte-identical across two runs
```

Mechanism: every ZIP entry is written with a fixed `date_time=(1980,1,1,0,0,0)` (the DOS epoch —
`zipfile` can't encode anything earlier), entries are added in a fixed sorted order with
`mimetype` forced first and `ZIP_STORED` (EPUB OCF requirement), and `ZipInfo.create_system` is
pinned to `0` so the archive doesn't pick up the host OS's value. MOBI/FB2 files are plain
byte-for-byte deterministic writes (no timestamps involved).

## Known gaps / deferred (for whoever picks this up next)

- The fetched EPUBs' "first paragraph" column is Gutenberg's own title-page boilerplate, not the
  narrative opening — Task 5 (actual device imports) should record what the app's hero
  excerpt/first-chapter text actually shows for each, since `EpubParser`/`HtmlChapterExtractor`
  may or may not treat that title page as its own chapter.
- `full-features.fb2`'s long `<cite>` is a repeated-sentence block well past 5000 characters (by
  construction, not hand-written prose) — matches the spec's letter ("a cite > 5000 chars") but
  isn't naturalistic text.
- No separate `F-LEGACY`/`F-EMPTY1` *files* were built: `F-LEGACY` (J-RDR-14/15) is a **seeded app
  data/profile state** from a prior Play build, not a book — out of scope for this task.
  `F-EMPTY1` is covered by the existing `empty.epub`/`empty-book.epub` pair.
- `app/src/androidTest/assets/qa-pd/gutenberg-11.epub` (plain Alice, the brief's "plain English
  novel EPUB") was left at its existing path rather than copied into `e2e/books/` — flag if a
  later task wants every fixture under one directory for a single `adb push`.
