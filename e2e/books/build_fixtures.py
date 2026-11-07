#!/usr/bin/env python3
"""Builds the synthetic fixture books for the Mirrolit Maestro E2E suite.

Deterministic: every ZIP entry gets a fixed timestamp (1980-01-01 00:00:00, the DOS epoch — the
oldest timestamp a standard ZIP can hold), entries are written in a fixed (sorted, with `mimetype`
forced first) order, and `mimetype` is STORED uncompressed per the EPUB OCF spec. Running this
script twice produces byte-identical files (see the `--verify` flag and the task report for a
sha256 proof).

Ported from one-off scratchpad generators written earlier in this project (mixed_epub.py,
mixed_epub2.py: the multi-language "letters" EPUBs; mkepub.py: the small single-language EPUB
builder pattern) — rewritten here as the single source of truth for `e2e/books/`.

Run: python3 e2e/books/build_fixtures.py [--out DIR] [--verify] [--large]

`--large` additionally writes `oversized.fb2` (~320 MB) into `fetched/` (gitignored) — the file
that must NOT be committed; it is only built on request, never by a plain run.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import os
import struct
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
FIXED_DATE = (1980, 1, 1, 0, 0, 0)

CONTAINER_XML = (
    '<?xml version="1.0"?>'
    '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
    '<rootfiles><rootfile full-path="OEBPS/content.opf" '
    'media-type="application/oebps-package+xml"/></rootfiles></container>'
)

def _make_1x1_png(r: int, g: int, b: int) -> bytes:
    """Builds a real, decodable 1x1 PNG of the given color (no external deps)."""
    import struct as _struct
    import zlib as _zlib

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (_struct.pack(">I", len(data)) + tag + data
                + _struct.pack(">I", _zlib.crc32(tag + data) & 0xFFFFFFFF))

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = _struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)  # 1x1, 8-bit, RGB
    raw = b"\x00" + bytes([r, g, b])  # filter byte 0 + one RGB pixel
    idat = _zlib.compress(raw, 9)
    return sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")


# Real (tiny) 1x1 pixel PNGs, used wherever a fixture needs an actual decodable image.
PNG_1X1_BLUE = _make_1x1_png(0x1E, 0x40, 0xC8)
PNG_1X1_RED = _make_1x1_png(0xC8, 0x20, 0x20)


# ─────────────────────────── deterministic zip writer ───────────────────────────

def write_zip(path: str, entries: list[tuple[str, bytes, int]]) -> None:
    """entries: list of (archive_name, data, compress_type). Written in list order, fixed date."""
    tmp = path + ".tmp"
    with zipfile.ZipFile(tmp, "w") as z:
        for name, data, ctype in entries:
            info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
            info.compress_type = ctype
            info.external_attr = 0o644 << 16
            info.create_system = 0  # FAT, not a host-specific value — avoids host-dependent bytes
            z.writestr(info, data, compress_type=ctype)
    os.replace(tmp, path)


def epub_entries(opf: str, nav: str, chapters: dict[str, str], extra_files: dict[str, bytes] | None = None):
    entries: list[tuple[str, bytes, int]] = [
        ("mimetype", b"application/epub+zip", zipfile.ZIP_STORED),
        ("META-INF/container.xml", CONTAINER_XML.encode("utf-8"), zipfile.ZIP_DEFLATED),
        ("OEBPS/content.opf", opf.encode("utf-8"), zipfile.ZIP_DEFLATED),
        ("OEBPS/nav.xhtml", nav.encode("utf-8"), zipfile.ZIP_DEFLATED),
    ]
    for name in sorted(chapters):
        data = chapters[name]
        is_binary = name.lower().endswith((".png", ".jpg", ".jpeg"))
        ctype = zipfile.ZIP_STORED if is_binary else zipfile.ZIP_DEFLATED
        payload = data if isinstance(data, bytes) else data.encode("utf-8")
        entries.append((f"OEBPS/{name}", payload, ctype))
    for name in sorted((extra_files or {})):
        ctype = zipfile.ZIP_STORED if name.lower().endswith((".png", ".jpg", ".jpeg")) else zipfile.ZIP_DEFLATED
        entries.append((f"OEBPS/{name}", (extra_files or {})[name], ctype))
    return entries


def opf(identifier: str, title: str, author: str, language: str, item_ids: list[str],
        spine_ids: list[str], description: str | None = None, cover_id: str | None = None,
        extra_items: str = "") -> str:
    desc = f"<dc:description>{html.escape(description)}</dc:description>" if description else ""
    cover_meta = f'<meta name="cover" content="{cover_id}"/>' if cover_id else ""
    manifest_items = "".join(
        f'<item id="{i}" href="{i}.xhtml" media-type="application/xhtml+xml"/>' for i in item_ids
    )
    spine = "".join(f'<itemref idref="{i}"/>' for i in spine_ids)
    return (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        f'<dc:identifier id="id">{identifier}</dc:identifier>'
        f'<dc:title>{html.escape(title)}</dc:title>'
        f'<dc:creator>{html.escape(author)}</dc:creator>'
        f'<dc:language>{language}</dc:language>'
        f'{desc}{cover_meta}'
        '<meta property="dcterms:modified">1980-01-01T00:00:00Z</meta>'
        '</metadata>'
        f'<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>'
        f'{manifest_items}{extra_items}</manifest>'
        f'<spine>{spine}</spine></package>'
    )


def nav_xhtml(entries: list[tuple[str, str]]) -> str:
    items = "".join(f'<li><a href="{href}.xhtml">{html.escape(title)}</a></li>' for href, title in entries)
    return (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">'
        f'<head><title>Nav</title></head><body><nav epub:type="toc"><ol>{items}</ol></nav></body></html>'
    )


def chapter_html(title: str | None, paragraphs: list[str], lang: str | None = None, extra_body: str = "") -> str:
    lang_attr = f' xml:lang="{lang}"' if lang else ""
    heading = f"<h1>{html.escape(title)}</h1>" if title else ""
    body = "".join(f"<p>{p}</p>" for p in paragraphs)
    return (
        f'<?xml version="1.0" encoding="UTF-8"?>'
        f'<html xmlns="http://www.w3.org/1999/xhtml"{lang_attr}>'
        f'<head><title>{html.escape(title or "Chapter")}</title></head>'
        f'<body>{heading}{body}{extra_body}</body></html>'
    )


# ───────────────────────────── simple multi-chapter book builder ─────────────────────────────

def build_simple_epub(out: str, identifier: str, title: str, author: str, language: str,
                       chapters: list[tuple[str, list[str]]], description: str | None = None) -> None:
    """chapters: list of (chapter_title, [paragraphs])."""
    item_ids = [f"c{i+1}" for i in range(len(chapters))]
    files = {f"{cid}.xhtml": chapter_html(ctitle, paras, lang=language)
             for cid, (ctitle, paras) in zip(item_ids, chapters)}
    the_opf = opf(identifier, title, author, language, item_ids, item_ids, description=description)
    the_nav = nav_xhtml([(cid, ctitle) for cid, (ctitle, _) in zip(item_ids, chapters)])
    write_zip(out, epub_entries(the_opf, the_nav, files))


# ───────────────────────────────── language-sample prose ─────────────────────────────────
# Each paragraph below is a self-written sentence (or an own-words restatement of a public-domain
# idea) of >= 60 characters so ParagraphLanguage/DetectBookLanguageUseCase's weighted-length gate
# (MIN_CHARS=60, MIN_PARAGRAPH=40, counted in "letters" with CJK at weight 3) treats it as a real
# sample of that language.

LANG_BOOKS = {
    "fi-book.epub": ("fi", "Syksyn ilta Helsingissä", "Testikirjailija", [
        ("Ensimmäinen luku", [
            "Syksyinen sumu lepäsi pitkään kaupungin yllä, ja rantakadun lyhdyt loistivat himmeästi kuin ohuen kankaan läpi.",
            "Liisa käveli hitaasti rantaa pitkin ja ajatteli kirjettä, jonka hän oli aamulla saanut vanhalta ystävältään kaukaisesta kylästä.",
            "Kirjeessä ystävä kertoi, että koulun vieressä oleva vanha puutarha oli kukkinut uudelleen, vaikka kaikki luulivat puiden kuolleen.",
        ]),
        ("Toinen luku", [
            "Hän hymyili muistaessaan, kuinka he lapsina keräsivät siellä omenoita ja piiloutuivat sateelta ison päärynäpuun alle.",
            "Kotona häntä odotti kuuma minttutee, ja ikkunalla nukkui punainen kissa käpertyneenä lämpimäksi kerälle.",
            "Ilta oli hiljainen, ja kaukaa kuului vain satama-alueen hiljainen kolkutus sekä tuulen humina puiden latvoissa.",
        ]),
        ("Kolmas luku", [
            "Seuraavana aamuna Liisa päätti vastata kirjeeseen pitkästi ja kertoa kaikesta, mitä kaupungissa oli tapahtunut.",
            "Hän kirjoitti pöydän ääressä auringon noustessa, ja kynän rapina oli ainoa äänimerkki hiljaisessa huoneessa.",
        ]),
    ]),
    "de-book.epub": ("de", "Ein Herbstabend in Berlin", "Testautor", [
        ("Erstes Kapitel", [
            "An jenem Abend hing lange ein warmer Nebel über der Stadt, und die Laternen am Ufer leuchteten weich wie durch dünnen Stoff.",
            "Anna ging langsam am Fluss entlang und dachte an den Brief, den sie morgens von einem alten Freund aus einem fernen Dorf erhalten hatte.",
            "Im Brief schrieb er, der alte Garten neben der Schule sei wieder erblüht, obwohl alle glaubten, die Bäume würden nie wieder leben.",
        ]),
        ("Zweites Kapitel", [
            "Sie lächelte, als sie sich erinnerte, wie sie als Kinder dort Äpfel sammelten und sich vor dem Regen unter dem großen Birnbaum versteckten.",
            "Zu Hause wartete heißer Pfefferminztee auf sie, und auf der Fensterbank schlief eine rote Katze, zu einem warmen Knäuel zusammengerollt.",
        ]),
        ("Drittes Kapitel", [
            "Am nächsten Morgen beschloss Anna, ausführlich zu antworten und alles zu erzählen, was in der Stadt geschehen war.",
            "Sie schrieb am Tisch, während die Sonne aufging, und das Kratzen der Feder war das einzige Geräusch im stillen Zimmer.",
        ]),
    ]),
    "es-book.epub": ("es", "Una tarde de otoño en Madrid", "Autor de Prueba", [
        ("Capítulo primero", [
            "Aquella tarde una niebla cálida permaneció largo tiempo sobre la ciudad, y los faroles del paseo brillaban suaves como tras una tela fina.",
            "Ana caminaba despacio junto al río pensando en la carta que había recibido esa mañana de un viejo amigo de un pueblo lejano.",
            "En la carta él contaba que el viejo jardín junto a la escuela había florecido de nuevo, aunque todos creían que los árboles ya no vivirían.",
        ]),
        ("Capítulo segundo", [
            "Sonrió al recordar cómo de niños recogían manzanas allí y se escondían de la lluvia bajo el gran peral del fondo.",
            "En casa la esperaba un té caliente de menta, y en el alféizar dormía un gato rojo enroscado como un cálido ovillo.",
        ]),
        ("Capítulo tercero", [
            "A la mañana siguiente Ana decidió responder largamente y contar todo lo que había ocurrido en la ciudad aquel otoño.",
            "Escribió en la mesa mientras salía el sol, y el rasguido de la pluma era el único sonido en la habitación silenciosa.",
        ]),
    ]),
    "ru-book.epub": ("ru", "Осенний вечер в Москве", "Тестовый автор", [
        ("Глава первая", [
            "В тот вечер над городом долго висел тёплый туман, и фонари на набережной светили мягко, будто через тонкую ткань.",
            "Анна медленно шла вдоль реки и думала о письме, которое получила утром от старого друга из далёкой деревни.",
            "В письме он писал, что старый сад рядом со школой снова зацвёл, хотя все думали, что деревья больше никогда не оживут.",
        ]),
        ("Глава вторая", [
            "Она улыбнулась, вспомнив, как в детстве они собирали там яблоки и прятались от дождя под большой грушей.",
            "Дома её ждал горячий чай с мятой, а на подоконнике спал рыжий кот, свернувшись тёплым клубком.",
        ]),
        ("Глава третья", [
            "На следующее утро Анна решила ответить длинным письмом и рассказать всё, что случилось в городе той осенью.",
            "Она писала за столом, пока вставало солнце, и скрип пера был единственным звуком в тихой комнате.",
        ]),
    ]),
    "no-book.epub": ("no", "En høstkveld i Oslo", "Testforfatter", [
        ("Første kapittel", [
            "Den kvelden lå en varm tåke lenge over byen, og lyktene langs brygga lyste svakt, som gjennom et tynt stoff.",
            "Anna gikk sakte langs elven og tenkte på brevet hun hadde fått om morgenen fra en gammel venn i en fjern landsby.",
            "I brevet skrev han at den gamle hagen ved skolen hadde blomstret igjen, selv om alle trodde trærne aldri ville leve mer.",
        ]),
        ("Andre kapittel", [
            "Hun smilte da hun husket hvordan de som barn plukket epler der og gjemte seg for regnet under det store pæretreet.",
            "Hjemme ventet en varm peppermynteteen på henne, og på vinduskarmen sov en rød katt, krøllet sammen som en varm dott.",
        ]),
        ("Tredje kapittel", [
            "Neste morgen bestemte Anna seg for å svare utfyllende og fortelle alt som hadde hendt i byen den høsten.",
            "Hun skrev ved bordet mens solen stod opp, og pennens skraping var den eneste lyden i det stille rommet.",
        ]),
    ]),
}


def build_lang_books(out_dir: str) -> list[str]:
    built = []
    for fname, (lang, title, author, chapters) in LANG_BOOKS.items():
        build_simple_epub(os.path.join(out_dir, fname), f"mirrolit-e2e-{fname}", title, author, lang,
                           chapters, description=f"A short {lang} novel, written for the Mirrolit E2E suite.")
        built.append(fname)
    return built


def build_und_book(out_dir: str) -> str:
    """Text whose language can't be detected: numbers, symbols, punctuation only."""
    name = "und-book.epub"
    paras = [
        "1234567890 -- 2026/11/07 §¶ 42 100% 3.14159 ####### @@@ ~~~ ::: ;;; <<<>>> [[[ ]]] ((( )))",
        "00 11 22 33 44 55 66 77 88 99 -- 10 20 30 40 50 60 70 80 90 -- ### $$$ %%% ^^^ &&& *** +++ ===",
        "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35",
    ]
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-und", "Undetectable Text Sample",
                       "Test Author", "und",
                       [("Section One", paras), ("Section Two", paras)],
                       description="A book whose text carries no detectable natural language.")
    return name


# ─────────────────────────────── CJK fixtures ───────────────────────────────

JA_POEM_LINES = [
    "古池や蛙飛びこむ水の音",
    "秋深き隣は何をする人ぞ",
    "夏草や兵どもが夢の跡",
    "閑さや岩にしみ入る蝉の声",
    "旅に病んで夢は枯野をかけ廻る",
]

JA_PREFACE_PARAGRAPHS = [
    "This collection gathers a handful of short poems written in the autumn months, each no more than a single breath long, "
    "and the editor has added this long preface in English so a reader unfamiliar with the form has somewhere to start before "
    "the poems themselves begin on the following pages, which are deliberately brief and spare by comparison with this note.",
    "The translator's introduction continues here, describing how each poem was chosen for its stillness rather than its drama, "
    "and noting that the Japanese text on the facing lines is reproduced exactly as found, without modernization of its characters, "
    "so that the rhythm of the original is preserved for anyone reading it alongside a translation on a split screen.",
]


def build_ja_poems(out_dir: str) -> str:
    name = "ja-poems.epub"
    preface_html = "".join(f"<p>{p}</p>" for p in JA_PREFACE_PARAGRAPHS)
    poems_html = "".join(f"<p>{html.escape(p)}</p>" for p in JA_POEM_LINES)
    files = {
        "c1.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
            f'<head><title>Preface</title></head><body><h1>Preface</h1>{preface_html}</body></html>'
        ),
        "c2.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ja">'
            f'<head><title>Poems</title></head><body><h1>Poems</h1>{poems_html}</body></html>'
        ),
    }
    the_opf = opf("mirrolit-e2e-ja-poems", "Short Autumn Poems", "Test Author", "en", ["c1", "c2"], ["c1", "c2"],
                  description="A long English preface followed by short Japanese poems.")
    the_nav = nav_xhtml([("c1", "Preface"), ("c2", "Poems")])
    write_zip(os.path.join(out_dir, name), epub_entries(the_opf, the_nav, files))
    return name


def _repeat_to_weighted(base_sentences: list[str], min_weighted_chars: int) -> list[str]:
    """Repeat/extend CJK sentences until each paragraph is well past the weighted-length gate."""
    out = []
    for s in base_sentences:
        # CJK counts 3x; duplicate the sentence until it is comfortably long.
        p = s
        while len(p) * 3 < min_weighted_chars:
            p += s
        out.append(p)
    return out


JA_NOVEL_SENTENCES = [
    "その夜、町の上には長いあいだ暖かい霧が垂れ込めていて、川沿いの街灯はまるで薄い布の向こうから漏れるように柔らかく光っていた。",
    "アンナは川沿いをゆっくりと歩きながら、遠い村に住む古い友人から今朝届いた手紙のことを考えていた。",
    "手紙には、学校の隣にある古い庭がまた花を咲かせたと書かれていて、誰もが木はもう生き返らないと思っていたのに、と続いていた。",
]

ZH_NOVEL_SENTENCES = [
    "那天晚上，温暖的雾气在城市上空久久不散，河边的路灯发出柔和的光，仿佛透过一层薄薄的布料。",
    "安娜沿着河边慢慢走着，想着今天早晨从远方村庄的老朋友那里收到的那封信。",
    "信里说，学校旁边那座老花园又开花了，尽管大家都以为那些树再也不会复活了。",
]


def build_ja_novel(out_dir: str) -> str:
    name = "ja-novel.epub"
    paras = _repeat_to_weighted(JA_NOVEL_SENTENCES, 2200)
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-ja-novel", "長い章のテスト小説", "テスト作者", "ja",
                       [("第一章", paras), ("第二章", paras)],
                       description="A long Japanese novel chapter for sentence-splitting/engine tests.")
    return name


def build_zh_novel(out_dir: str) -> str:
    name = "zh-novel.epub"
    paras = _repeat_to_weighted(ZH_NOVEL_SENTENCES, 2200)
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-zh-novel", "长章节测试小说", "测试作者", "zh",
                       [("第一章", paras), ("第二章", paras)],
                       description="A long Chinese novel chapter (2000+ weighted chars) for sentence tests.")
    return name


# ─────────────────────────────── mixed-language "letters" books ───────────────────────────────
# Ported from the scratchpad's mixed_epub.py / mixed_epub2.py (en narrative + embedded foreign
# letters), extended per docs/e2e/e2e_journeys.md's `mixed-lang.epub` spec: a German paragraph, a
# French letter, a Latin epigraph, a Russian paragraph, a short (<20 char) Chinese quote, a short
# Japanese line, and a one-line (<60 char) Latin fragment.

def build_mixed_lang(out_dir: str) -> str:
    name = "mixed-lang.epub"
    paras = [
        "It was late in the autumn when the letter finally arrived at the old house by the river, "
        "and nobody in the family could remember who had last written to the professor.",
        "He opened it slowly, reading the first lines aloud to his daughter, who had been waiting "
        "by the window for most of the afternoon.",
        "Lieber Freund, ich schreibe dir aus Berlin, wo der Winter schon früh begonnen hat. "
        "Die Straßen sind voller Schnee, und ich denke oft an unsere Gespräche am Fluss.",
        "His daughter laughed and asked him to read the rest, but the next page was in another "
        "hand entirely, and in another language too.",
        "Mon cher ami, je vous écris de Paris pour vous dire que votre livre est enfin arrivé "
        "chez l'imprimeur. Tout le monde ici attend la suite avec impatience.",
        "At the bottom of the envelope there was a small card with a line of verse that the "
        "professor had copied out many years before, in a language older than any of the letters.",
        "Arma virumque cano, Troiae qui primus ab oris Italiam, fato profugus, Laviniaque venit "
        "litora, multum ille et terris iactatus et alto.",
        "And finally, folded twice, a note from his old colleague in Moscow, which he did not "
        "need to translate for anyone in the room.",
        "Дорогой друг, пишу "
        "тебе из Москвы. Здесь "
        "всё по-прежнему, только "
        "зима в этом году пришла "
        "раньше обычного.",
        "Tucked inside that last letter was a single small card bearing only four characters, "
        "clipped from somewhere else entirely:",
        "你好啊！",  # short (<20 char) Chinese quote
        "And under it, in a different hand, a short line that was not translated either:",
        "こんにちは",  # short Japanese line
        "He put the letters away and went to make tea, while the rain began again outside and the "
        "river rose a little higher against the stones.",
        "Et sic finis.",  # one-line Latin fragment, < 60 chars
    ]
    ch = "".join(f"<p>{p}</p>" for p in paras)
    files = {
        "c1.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
            f'<head><title>The Letter</title></head><body><h1>The Letter</h1>{ch}</body></html>'
        ),
    }
    the_opf = opf("mirrolit-e2e-mixed-lang", "Letters in Many Tongues", "Test Author", "en", ["c1"], ["c1"],
                  description="An English narrative with embedded letters in six other languages.")
    the_nav = nav_xhtml([("c1", "The Letter")])
    write_zip(os.path.join(out_dir, name), epub_entries(the_opf, the_nav, files))
    return name


def build_letter_prague(out_dir: str) -> str:
    """Ported from mixed_epub2.py: the same letters, with the Russian one swapped for Czech — the
    'Czech-paragraph book' fixture (a Czech paragraph inside an otherwise-English/German/French book)."""
    name = "letter-prague.epub"
    paras = [
        "It was late in the autumn when the letter finally arrived at the old house by the river, "
        "and nobody in the family could remember who had last written to the professor.",
        "He opened it slowly, reading the first lines aloud to his daughter, who had been waiting "
        "by the window for most of the afternoon.",
        "Lieber Freund, ich schreibe dir aus Berlin, wo der Winter schon früh begonnen hat. "
        "Die Straßen sind voller Schnee, und ich denke oft an unsere Gespräche am Fluss.",
        "His daughter laughed and asked him to read the rest, but the next page was in another "
        "hand entirely, and in another language too.",
        "Mon cher ami, je vous écris de Paris pour vous dire que votre livre est enfin arrivé "
        "chez l'imprimeur. Tout le monde ici attend la suite avec impatience.",
        "At the bottom of the envelope there was a small card with a line of verse that the "
        "professor had copied out many years before.",
        "Arma virumque cano, Troiae qui primus ab oris Italiam, fato profugus, Laviniaque venit "
        "litora, multum ille et terris iactatus et alto.",
        "And finally, folded twice, a note from an old colleague in Prague, which did not need "
        "translating for anyone at the table.",
        "Milý příteli, píši ti z Prahy, kde už napadl první sníh. "
        "Na Karlově mostě je ticho a já často vzpomínam na naše dlouhé "
        "procházky podél řeky.",
        "He put the letters away and went to make tea, while the rain began again outside and the "
        "river rose a little higher against the stones.",
    ]
    ch = "".join(f"<p>{p}</p>" for p in paras)
    files = {
        "c1.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
            f'<head><title>The Letter</title></head><body><h1>The Letter</h1>{ch}</body></html>'
        ),
    }
    the_opf = opf("mirrolit-e2e-letter-prague", "A Letter from Prague", "Test Author", "en", ["c1"], ["c1"],
                  description="An English narrative with a Czech letter inside it.")
    the_nav = nav_xhtml([("c1", "The Letter")])
    write_zip(os.path.join(out_dir, name), epub_entries(the_opf, the_nav, files))
    return name


# ───────────────────────────── English novel / structural fixtures ─────────────────────────────

RIVER_PARAGRAPHS = [
    "The old mill still stood at the bend of the river, though its wheel had not turned since the "
    "summer the millpond ran dry and the fish went with it downstream.",
    "Every morning the ferryman crossed before the mist had lifted, and every evening he crossed "
    "back again, so that the villagers set their clocks by the creak of his single oar.",
    "Nobody remembered exactly when the bridge had washed out, only that the detour through the "
    "orchard had become so familiar it no longer felt like a detour at all.",
    "In the long grass behind the chapel there was a stone with no name on it, and the children "
    "had decided among themselves, without ever discussing it, never to step on that patch of ground.",
    "The schoolmaster kept a ledger of the weather going back thirty years, and on wet days he read "
    "from it aloud, as if the numbers themselves were a kind of story worth telling.",
]


def build_en_novel(out_dir: str) -> str:
    """F-LONG: >= 20 chapters, plain text, chapter mastheads; ch1 has 40+ paragraphs, ch3 is long
    (3+ screens), and a later chapter is deliberately bookmarkable."""
    name = "en-novel.epub"
    chapters: list[tuple[str, list[str]]] = []
    ch1 = [RIVER_PARAGRAPHS[i % len(RIVER_PARAGRAPHS)] + f" ({i + 1})" for i in range(42)]
    chapters.append(("Chapter 1 — The Ferry", ch1))
    for n in range(2, 23):
        count = 60 if n == 3 else 8  # chapter 3 is long: 3+ screens of paragraphs
        paras = [RIVER_PARAGRAPHS[i % len(RIVER_PARAGRAPHS)] + f" (ch{n}.{i + 1})" for i in range(count)]
        title = f"Chapter {n}" + (" — The Long Crossing" if n == 3 else "")
        chapters.append((title, paras))
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-en-novel", "The River Crossing",
                       "Test Author", "en", chapters,
                       description="A plain English novel: 22 chapters, chapter 1 has 40+ paragraphs, "
                                    "chapter 3 runs long, used as the general-purpose long English fixture.")
    return name


def build_short_novel(out_dir: str) -> str:
    """F-SHORT (reader journeys): a short, single-chapter English EPUB (~30 paragraphs)."""
    name = "short-novel.epub"
    paras = [RIVER_PARAGRAPHS[i % len(RIVER_PARAGRAPHS)] + f" ({i + 1})" for i in range(30)]
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-short-novel", "A Short Crossing",
                       "Test Author", "en", [("Chapter 1", paras)],
                       description="A short single-chapter English novel (~30 paragraphs).")
    return name


def build_plain_prose(out_dir: str) -> str:
    """F-PROSE: plain prose, no tables (position regression)."""
    name = "plain-prose.epub"
    chapters = []
    for n in range(1, 5):
        paras = [RIVER_PARAGRAPHS[i % len(RIVER_PARAGRAPHS)] + f" (ch{n}.{i + 1})" for i in range(14)]
        chapters.append((f"Chapter {n}", paras))
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-plain-prose", "Plain Prose",
                       "Test Author", "en", chapters,
                       description="Plain prose, no tables — a position-regression fixture.")
    return name


def build_long_chapter(out_dir: str) -> str:
    """One chapter with 500+ paragraphs."""
    name = "long-chapter.epub"
    paras = [RIVER_PARAGRAPHS[i % len(RIVER_PARAGRAPHS)] + f" (p{i + 1})" for i in range(520)]
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-long-chapter", "One Very Long Chapter",
                       "Test Author", "en", [("Chapter 1", paras)],
                       description="A single chapter with 500+ paragraphs, for fling/jump tests.")
    return name


def build_english_with_french(out_dir: str) -> str:
    """F-FOREIGN: English novel with an embedded French passage in chapter 1."""
    name = "english-with-french.epub"
    ch1 = list(RIVER_PARAGRAPHS) + [
        "Mon cher ami, je vous écris de Paris pour vous dire que votre livre est enfin arrivé "
        "chez l'imprimeur, et que tout le monde ici attend la suite avec une impatience grandissante.",
    ] + RIVER_PARAGRAPHS[:2]
    chapters = [("Chapter 1", ch1), ("Chapter 2", RIVER_PARAGRAPHS)]
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-en-fr", "An English Novel",
                       "Test Author", "en", chapters,
                       description="An English novel with one embedded French passage in chapter 1.")
    return name


def build_no_description(out_dir: str) -> str:
    """F-NODESC: no dc:description; first body paragraph > 40 chars."""
    name = "no-description.epub"
    chapters = [("Chapter 1", RIVER_PARAGRAPHS), ("Chapter 2", RIVER_PARAGRAPHS)]
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-nodesc", "No Description Here",
                       "Test Author", "en", chapters, description=None)
    return name


def build_tiny(out_dir: str) -> str:
    """F-TINY: no description; first paragraph <= 40 chars."""
    name = "tiny.epub"
    paras = ["A very short first line."] + RIVER_PARAGRAPHS[:2]
    build_simple_epub(os.path.join(out_dir, name), "mirrolit-e2e-tiny", "Tiny Book",
                       "Test Author", "en", [("Chapter 1", paras)], description=None)
    return name


def build_cover_b(out_dir: str) -> str:
    """F-COVER-B: small EPUB with its own, visibly different (blue) cover."""
    name = "cover-b.epub"
    files = {
        "cover.png": PNG_1X1_BLUE,
        "c1.xhtml": chapter_html("Chapter 1", RIVER_PARAGRAPHS[:3]),
    }
    manifest_extra = '<item id="cover-img" href="cover.png" media-type="image/png" properties="cover-image"/>'
    the_opf = opf("mirrolit-e2e-cover-b", "The Blue Cover Book", "Test Author", "en", ["c1"], ["c1"],
                  description="A small EPUB with its own, visibly different (blue) cover.",
                  cover_id="cover-img", extra_items=manifest_extra)
    the_nav = nav_xhtml([("c1", "Chapter 1")])
    write_zip(os.path.join(out_dir, name), epub_entries(the_opf, the_nav, files))
    return name


def build_image_chapter(out_dir: str) -> str:
    """F-IMGCH / F-IMGFILES: EPUB with one chapter file that holds only an illustration, no h1-h3."""
    name = "image-chapter.epub"
    files = {
        "plate.png": PNG_1X1_RED,
        "c1.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
            '<head><title>Plate</title></head><body><img src="plate.png" alt="plate"/></body></html>'
        ),
        "c2.xhtml": chapter_html("Chapter One", RIVER_PARAGRAPHS[:3]),
    }
    manifest_extra = '<item id="plate-img" href="plate.png" media-type="image/png"/>'
    the_opf = opf("mirrolit-e2e-imgch", "Image Chapter Book", "Test Author", "en", ["c1", "c2"], ["c1", "c2"],
                  description="A content file with only an illustration, before the first text chapter.",
                  extra_items=manifest_extra)
    the_nav = nav_xhtml([("c1", "Plate"), ("c2", "Chapter One")])
    write_zip(os.path.join(out_dir, name), epub_entries(the_opf, the_nav, files))
    return name


def build_pictures_only(out_dir: str) -> str:
    """F-PICS / F-IMGONLY: book made only of images, no text, no language metadata."""
    name = "pictures-only.epub"
    files = {
        "p1.png": PNG_1X1_RED,
        "p2.png": PNG_1X1_BLUE,
        "c1.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
            '<head><title>Pictures</title></head><body>'
            '<img src="p1.png" alt="one"/><img src="p2.png" alt="two"/>'
            '</body></html>'
        ),
    }
    manifest_extra = (
        '<item id="p1-img" href="p1.png" media-type="image/png"/>'
        '<item id="p2-img" href="p2.png" media-type="image/png"/>'
    )
    # Deliberately no <dc:language> element — language metadata is absent for this fixture.
    the_opf = (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        '<dc:identifier id="id">mirrolit-e2e-pics-only</dc:identifier>'
        '<dc:title>Pictures Only</dc:title><dc:creator>Test Author</dc:creator>'
        '</metadata>'
        f'<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>'
        f'<item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>{manifest_extra}</manifest>'
        '<spine><itemref idref="c1"/></spine></package>'
    )
    the_nav = nav_xhtml([("c1", "Pictures")])
    write_zip(os.path.join(out_dir, name), epub_entries(the_opf, the_nav, files))
    return name


def build_empty_first_chapter(out_dir: str) -> str:
    """F-EMPTY-CH1 / `empty-chapter.epub`: first chapter has zero paragraphs; chapter 2 has content."""
    files = {
        "c1.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
            '<head><title>Empty</title></head><body><h1>An Empty Chapter</h1></body></html>'
        ),
        "c2.xhtml": chapter_html("Chapter Two", RIVER_PARAGRAPHS[:3]),
    }
    the_opf = opf("mirrolit-e2e-empty-ch1", "Empty First Chapter", "Test Author", "en", ["c1", "c2"], ["c1", "c2"],
                  description="The first chapter has a heading but zero paragraphs.")
    the_nav = nav_xhtml([("c1", "An Empty Chapter"), ("c2", "Chapter Two")])
    write_zip(os.path.join(out_dir, "empty-first-chapter.epub"), epub_entries(the_opf, the_nav, files))
    write_zip(os.path.join(out_dir, "empty-chapter.epub"), epub_entries(the_opf, the_nav, files))
    return "empty-first-chapter.epub / empty-chapter.epub (identical content, two names)"


def build_empty_book(out_dir: str) -> str:
    """F-EMPTY / `empty-book.epub`: valid container, no extractable content at all."""
    files = {
        "c1.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
            '<head><title>Empty</title></head><body></body></html>'
        ),
    }
    the_opf = opf("mirrolit-e2e-empty", "Empty Book", "Test Author", "en", ["c1"], ["c1"],
                  description="A valid EPUB container with no extractable content.")
    the_nav = nav_xhtml([("c1", "Empty")])
    write_zip(os.path.join(out_dir, "empty.epub"), epub_entries(the_opf, the_nav, files))
    write_zip(os.path.join(out_dir, "empty-book.epub"), epub_entries(the_opf, the_nav, files))
    return "empty.epub / empty-book.epub (identical content, two names)"


def build_short_header(out_dir: str) -> str:
    """F-SHORT (library/ERR-008): shorter than the format-sniff header-read length."""
    name = "short-header.epub"
    with open(os.path.join(out_dir, name), "wb") as f:
        f.write(b"PK\x03\x04")  # 4 bytes: a local-file-header magic and nothing else
    return name


def build_corrupt_epub(out_dir: str) -> str:
    """F-CORRUPT-EPUB: truncated/broken zip, .epub extension."""
    name = "corrupt.epub"
    # A plausible-looking but truncated ZIP: real local-file-header magic, garbage lengths, no data.
    data = b"PK\x03\x04" + bytes(26) + b"mimetypeapplication/epub+zip" + b"\x00" * 10
    with open(os.path.join(out_dir, name), "wb") as f:
        f.write(data)
    return name


def build_zipbomb(out_dir: str) -> str:
    """F-ZIPBOMB: inflates far past MAX_DECOMPRESSED (~300 MB). DEFLATE's 32 KB window caps the
    compression ratio at roughly 1000:1 for even the most repetitive input, so a container built
    this way is itself ~300+ KB on disk — too big for the <200 KB committed budget. Built only
    with --large, straight into fetched/ (gitignored), like oversized.fb2."""
    name = "zipbomb.epub"
    big_zero_chapter = b"0" * (320 * 1024 * 1024)  # 320 MB of a single repeated byte
    tmp = os.path.join(out_dir, name + ".tmp")
    # NOTE: ZipInfo's own compress_type defaults to ZIP_STORED; writestr only honors the
    # ZipFile-level default when NO ZipInfo object is passed, so compress_type must be passed
    # explicitly on every call below (a first version of this omitted it and silently stored the
    # 320 MB chapter uncompressed).
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        z.writestr(zipfile.ZipInfo("mimetype", date_time=FIXED_DATE), b"application/epub+zip",
                   compress_type=zipfile.ZIP_STORED)
        z.writestr(zipfile.ZipInfo("META-INF/container.xml", date_time=FIXED_DATE), CONTAINER_XML,
                   compress_type=zipfile.ZIP_DEFLATED)
        z.writestr(zipfile.ZipInfo("OEBPS/content.opf", date_time=FIXED_DATE),
                   opf("mirrolit-e2e-zipbomb", "Zip Bomb", "Test Author", "en", ["c1"], ["c1"]),
                   compress_type=zipfile.ZIP_DEFLATED)
        z.writestr(zipfile.ZipInfo("OEBPS/nav.xhtml", date_time=FIXED_DATE), nav_xhtml([("c1", "Chapter 1")]),
                   compress_type=zipfile.ZIP_DEFLATED)
        z.writestr(zipfile.ZipInfo("OEBPS/c1.xhtml", date_time=FIXED_DATE), big_zero_chapter,
                   compress_type=zipfile.ZIP_DEFLATED)
    os.replace(tmp, os.path.join(out_dir, name))
    return name


# ─────────────────────────────────────── FB2 fixtures ───────────────────────────────────────

FB2_HEADER_UTF8 = '<?xml version="1.0" encoding="utf-8"?>'

FULL_FEATURES_FB2 = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
<description>
<title-info>
<book-title>The Complete Letters</book-title>
<author><first-name>First</first-name><last-name>Last</last-name></author>
<annotation><p>A short demonstration novella collecting letters, verse and a long aside, built to exercise every FB2 feature the Mirrolit parser understands.</p></annotation>
<coverpage><image l:href="#cover.png" xmlns:l="http://www.w3.org/1999/xlink"/></coverpage>
</title-info>
</description>
<body>
<epigraph>
<p>Every letter is a kind of arrival, however long delayed by weather, distance or doubt.</p>
<text-author>An old saying</text-author>
</epigraph>
<section>
<title><p>Part One</p></title>
<section>
<title><p>The First Letter</p></title>
<epigraph>
<p>Even the shortest letter carries the whole weight of the one who wrote it.</p>
<text-author>Unknown</text-author>
</epigraph>
<p>The letter arrived on a grey morning, long after everyone had stopped expecting it, and the professor read it twice before he said a word to anyone in the house.</p>
<p>He folded it along its old crease and set it on the table, where it stayed for the rest of that long and otherwise unremarkable day.</p>
<poem>
<title><p>A Verse Enclosed</p></title>
<stanza>
<v>The river does not wait for spring,</v>
<v>nor spring for any river's leave,</v>
<v>and still the letters come and cling</v>
<v>to names that no one dares to grieve.</v>
</stanza>
</poem>
<subtitle>A note in another hand</subtitle>
<cite>
<p>""" + ("This single long quotation runs on for a very great while, repeating its own cadence until it has, by sheer accumulation, passed five thousand characters of enclosed, cited prose that nobody in the house had the patience to read aloud in one sitting, though everyone agreed it was, in its way, rather beautiful and worth keeping for that reason alone. " * 20) + """</p>
</cite>
</section>
<section>
<title><p>The Second Letter</p></title>
<p>Unlike the first, this letter was short, and its handwriting suggested it had been written standing up, perhaps at a window, perhaps in a hurry.</p>
<p>Still, it ended the way all the good ones did, with a promise to write again before the month was out.</p>
</section>
</section>
</body>
<binary id="cover.png" content-type="image/png">__COVER_B64__</binary>
</FictionBook>
"""

NO_CHAPTERS_FB2 = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
<description>
<title-info>
<book-title>No Qualifying Chapters</book-title>
<author><first-name>Test</first-name><last-name>Author</last-name></author>
</title-info>
</description>
<body>
<section>
<title><p>Wrapper</p></title>
<section>
<title><p>Empty Section One</p></title>
</section>
<section>
<title><p>Empty Section Two</p></title>
</section>
</section>
</body>
</FictionBook>
"""

RUSSIAN_1251_FB2 = """<?xml version="1.0" encoding="windows-1251"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
<description>
<title-info>
<book-title>Ветер над рекой</book-title>
<author><first-name>Иван</first-name><last-name>Иванов</last-name></author>
</title-info>
</description>
<body>
<section>
<title><p>Глава первая</p></title>
<p>В тот вечер над городом долго висел тёплый туман, и фонари на набережной светили мягко, будто сквозь тонкую ткань.</p>
<p>Катерина стояла у окна долго, словно ждала какого-то знака с другого берега, который так и не подали той ночью.</p>
</section>
</body>
</FictionBook>
"""


def build_full_features_fb2(out_dir: str) -> str:
    import base64
    data = FULL_FEATURES_FB2.replace("__COVER_B64__", base64.b64encode(PNG_1X1_BLUE).decode("ascii"))
    with open(os.path.join(out_dir, "full-features.fb2"), "w", encoding="utf-8") as f:
        f.write(data)
    return "full-features.fb2"


def build_no_chapters_fb2(out_dir: str) -> str:
    with open(os.path.join(out_dir, "no-chapters.fb2"), "w", encoding="utf-8") as f:
        f.write(NO_CHAPTERS_FB2)
    return "no-chapters.fb2"


def build_russian_1251_fb2(out_dir: str) -> str:
    with open(os.path.join(out_dir, "russian-1251.fb2"), "wb") as f:
        f.write(RUSSIAN_1251_FB2.encode("cp1251"))
    return "russian-1251.fb2"


def build_corrupt_fb2(out_dir: str) -> str:
    """F-CORRUPT-FB2: truncated mid-file."""
    data = FULL_FEATURES_FB2.encode("utf-8")[:120]  # cut off mid-tag, no closing tags at all
    with open(os.path.join(out_dir, "corrupt.fb2"), "wb") as f:
        f.write(data)
    return "corrupt.fb2"


# ─────────────────────────────────────── MOBI fixtures ───────────────────────────────────────
# Hand-rolled PDB + PalmDOC, matching MobiFile/MobiParser/MobiCodec exactly (see app/src/main/.../
# domain/parser/{MobiFile,MobiParser,MobiCodec}.kt): 78-byte PDB header, 8-byte record-info entries,
# then record data. Record 0 is a bare 16-byte PalmDOC header (compression/textRecordCount/
# encryption only) unless noted — short enough that MobiParser's `rec0.size >= 20` MOBI-header
# check is false, so title/author fall back to the PDB name field / "Unknown" (fine for these
# structural fixtures). ASCII bytes 0x09-0x7F are PalmDOC-literal (decode to themselves unchanged),
# so "compressed" (compression=2) text can be written byte-for-byte identical to the plain HTML.

def _pdb_header(name: bytes, record_count: int) -> bytes:
    header = bytearray(78)
    header[0:32] = name[:32].ljust(32, b"\x00")
    header[60:68] = b"BOOKMOBI"
    struct.pack_into(">H", header, 76, record_count)
    return bytes(header)


def _record_info(offset: int, index: int) -> bytes:
    return struct.pack(">IB", offset, 0) + bytes([0, 0, index & 0xFF])


def _palmdoc_header(compression: int, text_record_count: int, encryption: int) -> bytes:
    h = bytearray(16)
    struct.pack_into(">H", h, 0, compression)
    struct.pack_into(">H", h, 8, text_record_count)
    struct.pack_into(">H", h, 12, encryption)
    return bytes(h)


def _build_mobi(out_path: str, pdb_name: bytes, record0: bytes, text_records: list[bytes]) -> None:
    records = [record0] + text_records
    n = len(records)
    header = _pdb_header(pdb_name, n)
    info_table = b"".join(_record_info(0, i) for i in range(n))  # offsets patched below
    offset = len(header) + len(info_table)
    offsets = []
    for rec in records:
        offsets.append(offset)
        offset += len(rec)
    info_table = b"".join(_record_info(offsets[i], i) for i in range(n))
    with open(out_path, "wb") as f:
        f.write(header)
        f.write(info_table)
        for rec in records:
            f.write(rec)


def _html_record(html_text: str) -> bytes:
    return html_text.encode("ascii")  # all fixtures use plain ASCII HTML, by construction


def build_mobi_classic_h2(out_dir: str) -> str:
    chapters_html = "".join(
        f"<h2>Chapter {n}</h2><p>Chapter {n} opens here, on a {'quiet' if n % 2 else 'bright'} morning "
        f"by the old mill, where nothing much happened for a very long time.</p><p>Second paragraph of "
        f"chapter {n}, added only so the chapter has more than one line of text to show.</p>"
        for n in range(1, 5)
    )
    html_text = f"<html><body>{chapters_html}</body></html>"
    rec0 = _palmdoc_header(1, 1, 0)
    _build_mobi(os.path.join(out_dir, "classic-h2.mobi"), b"Classic H2 Test", rec0, [_html_record(html_text)])
    return "classic-h2.mobi"


def build_mobi_pagebreaks(out_dir: str) -> str:
    parts = [
        f"<p>Section {n}: the ferry crossed at dawn and the fog had not yet lifted from the water "
        f"when the first passengers arrived at the landing.</p>"
        for n in range(1, 5)
    ]
    html_text = "<html><body>" + "<mbp:pagebreak>".join(parts) + "</body></html>"
    rec0 = _palmdoc_header(1, 1, 0)
    _build_mobi(os.path.join(out_dir, "pagebreaks.mobi"), b"Pagebreaks Test", rec0, [_html_record(html_text)])
    return "pagebreaks.mobi"


def build_mobi_pagebreaks_and_h2(out_dir: str) -> str:
    regions = []
    for n in range(1, 4):
        regions.append(
            f"<h2>Inner Heading {n}</h2><p>Region {n} begins with its own h2 heading, which must not "
            f"subdivide the page-break-based chapter it lives inside.</p>"
        )
    html_text = "<html><body>" + "<mbp:pagebreak>".join(regions) + "</body></html>"
    rec0 = _palmdoc_header(1, 1, 0)
    _build_mobi(os.path.join(out_dir, "pagebreaks-and-h2.mobi"), b"PB+H2 Test", rec0, [_html_record(html_text)])
    return "pagebreaks-and-h2.mobi"


def build_mobi_h1_h2(out_dir: str) -> str:
    parts = []
    for n in range(1, 4):
        parts.append(
            f"<h1>Book {n}</h1><h2>Section {n}.1</h2><p>The first section of book {n} starts here, "
            f"with a nested h2 heading that should not create an extra chapter of its own.</p>"
        )
    html_text = "<html><body>" + "".join(parts) + "</body></html>"
    rec0 = _palmdoc_header(1, 1, 0)
    _build_mobi(os.path.join(out_dir, "h1-h2.mobi"), b"H1 H2 Test", rec0, [_html_record(html_text)])
    return "h1-h2.mobi"


def build_mobi_plain(out_dir: str) -> str:
    html_text = (
        "<html><body>"
        "<p>There are no headings and no page breaks anywhere in this short book, which exists only "
        "to prove that a classic MOBI with neither still opens as a single chapter, without a crash.</p>"
        "<p>This is the second and final paragraph of that one chapter.</p>"
        "</body></html>"
    )
    rec0 = _palmdoc_header(1, 1, 0)
    _build_mobi(os.path.join(out_dir, "plain.mobi"), b"Plain MOBI Test", rec0, [_html_record(html_text)])
    return "plain.mobi"


def build_mobi_distance0(out_dir: str) -> str:
    """compression=2 (PalmDOC); plain ASCII text is written byte-identical (literal range), with a
    crafted 0x80 0x00 back-reference pair (distance 0, length 3) inserted between two paragraphs —
    MobiCodec.palmDocDecompress must skip it rather than corrupt/hang (see MobiCodecTest). The
    distance-0 token sits right after the 1st paragraph's closing `</p>`, at byte offset
    `len(part_a)` into the single text record (part_a below) — i.e. right before "<p>The second"."""
    part_a = (
        "<html><body><h1>Distance Zero</h1>"
        "<p>The paragraph just before the crafted token reads normally, with nothing unusual in it "
        "at all, so a reader would not notice anything from the text alone.</p>"
    )
    part_b = (
        "<p>The second paragraph, just after the crafted distance-0 token, must also read normally: "
        "no corruption, no hang, no extra garbage bytes appended anywhere nearby.</p>"
        "</body></html>"
    )
    text_bytes = part_a.encode("ascii") + bytes([0x80, 0x00]) + part_b.encode("ascii")
    rec0 = _palmdoc_header(2, 1, 0)
    _build_mobi(os.path.join(out_dir, "palmdoc-distance0.mobi"), b"Distance0 Test", rec0, [text_bytes])
    return "palmdoc-distance0.mobi"


def build_mobi_huffcdic(out_dir: str) -> str:
    """F-HUFF: compression=17480 (HUFF/CDIC) — MobiParser rejects it before touching record data."""
    rec0 = _palmdoc_header(17480, 0, 0)
    _build_mobi(os.path.join(out_dir, "huffcdic.mobi"), b"HUFFCDIC Test", rec0, [])
    return "huffcdic.mobi"


def build_mobi_drm(out_dir: str) -> str:
    """F-DRM: PalmDOC encryption field non-zero — rejected before touching record data."""
    rec0 = _palmdoc_header(1, 0, 2)
    _build_mobi(os.path.join(out_dir, "drm.mobi"), b"DRM Test", rec0, [])
    return "drm.mobi"


def build_azw3(out_dir: str) -> str:
    """F-AZW3: a full MOBI header with fileVersion=8 (KF8/AZW3) at record0 offset 0x24 — the parser
    rejects this before reading any text records."""
    rec0 = bytearray(44)
    struct.pack_into(">H", rec0, 0, 1)   # compression = none
    struct.pack_into(">H", rec0, 12, 0)  # encryption = none
    rec0[16:20] = b"MOBI"
    struct.pack_into(">I", rec0, 20, 24)   # mobiHeaderLength (small, keeps later offset checks false)
    struct.pack_into(">I", rec0, 28, 1252)  # textEncoding
    struct.pack_into(">I", rec0, 36, 8)     # fileVersion = 8 -> AZW3/KF8
    _build_mobi(os.path.join(out_dir, "kf8.azw3"), b"KF8 AZW3 Test", bytes(rec0), [])
    return "kf8.azw3"


def build_corrupt_mobi(out_dir: str) -> str:
    """F-CORRUPT-MOBI: a PDB header claiming records that do not exist (truncated file)."""
    header = _pdb_header(b"Corrupt MOBI", 5)  # claims 5 records, but no record-info table follows
    with open(os.path.join(out_dir, "corrupt.mobi"), "wb") as f:
        f.write(header)
    return "corrupt.mobi"


# ───────────────────────────────────────── oversized (--large) ─────────────────────────────────────────

def build_oversized_fb2(fetched_dir: str) -> str:
    """F-HUGE: plain file far past a sane book size (~320 MB). Never committed — written straight
    into books/fetched/ (gitignored) and only when --large is passed."""
    name = "oversized.fb2"
    path = os.path.join(fetched_dir, name)
    chunk = (b"<p>" + b"x" * 996 + b"</p>\n")  # ~1000 bytes/paragraph
    target = 320 * 1024 * 1024
    with open(path, "wb") as f:
        f.write(b'<?xml version="1.0" encoding="utf-8"?><FictionBook><body><section><title><p>Oversized</p></title>')
        written = 0
        while written < target:
            f.write(chunk)
            written += len(chunk)
        f.write(b"</section></body></FictionBook>")
    return name


# ─────────────────────────────────────────── main ───────────────────────────────────────────

def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def build_all(out_dir: str) -> list[str]:
    os.makedirs(out_dir, exist_ok=True)
    built = []
    built += build_lang_books(out_dir)
    built.append(build_und_book(out_dir))
    built.append(build_ja_poems(out_dir))
    built.append(build_ja_novel(out_dir))
    built.append(build_zh_novel(out_dir))
    built.append(build_mixed_lang(out_dir))
    built.append(build_letter_prague(out_dir))
    built.append(build_en_novel(out_dir))
    built.append(build_short_novel(out_dir))
    built.append(build_plain_prose(out_dir))
    built.append(build_long_chapter(out_dir))
    built.append(build_english_with_french(out_dir))
    built.append(build_no_description(out_dir))
    built.append(build_tiny(out_dir))
    built.append(build_cover_b(out_dir))
    built.append(build_image_chapter(out_dir))
    built.append(build_pictures_only(out_dir))
    built.append(build_empty_first_chapter(out_dir))
    built.append(build_empty_book(out_dir))
    built.append(build_short_header(out_dir))
    built.append(build_corrupt_epub(out_dir))
    built.append(build_full_features_fb2(out_dir))
    built.append(build_no_chapters_fb2(out_dir))
    built.append(build_russian_1251_fb2(out_dir))
    built.append(build_corrupt_fb2(out_dir))
    built.append(build_mobi_classic_h2(out_dir))
    built.append(build_mobi_pagebreaks(out_dir))
    built.append(build_mobi_pagebreaks_and_h2(out_dir))
    built.append(build_mobi_h1_h2(out_dir))
    built.append(build_mobi_plain(out_dir))
    built.append(build_mobi_distance0(out_dir))
    built.append(build_mobi_huffcdic(out_dir))
    built.append(build_mobi_drm(out_dir))
    built.append(build_azw3(out_dir))
    built.append(build_corrupt_mobi(out_dir))
    return built


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=HERE)
    ap.add_argument("--verify", action="store_true", help="build twice into temp dirs and diff sha256")
    ap.add_argument("--large", action="store_true", help="also build oversized.fb2 into fetched/ (not committed)")
    args = ap.parse_args()

    if args.verify:
        import shutil
        import tempfile
        d1 = tempfile.mkdtemp()
        d2 = tempfile.mkdtemp()
        try:
            names1 = sorted(build_all(d1))
            names2 = sorted(build_all(d2))
            ok = True
            for name in sorted(set(os.listdir(d1)) | set(os.listdir(d2))):
                p1, p2 = os.path.join(d1, name), os.path.join(d2, name)
                if not (os.path.exists(p1) and os.path.exists(p2)):
                    print(f"MISMATCH (missing): {name}")
                    ok = False
                    continue
                s1, s2 = sha256_of(p1), sha256_of(p2)
                status = "OK" if s1 == s2 else "MISMATCH"
                if s1 != s2:
                    ok = False
                print(f"{status}  {name}  {s1}")
            print("VERIFY:", "PASS — byte-identical across two runs" if ok else "FAIL")
            return 0 if ok else 1
        finally:
            shutil.rmtree(d1, ignore_errors=True)
            shutil.rmtree(d2, ignore_errors=True)

    built = build_all(args.out)
    for name in sorted(os.listdir(args.out)):
        p = os.path.join(args.out, name)
        if os.path.isfile(p):
            print(f"{os.path.getsize(p):>10}  {sha256_of(p)}  {name}")

    if args.large:
        fetched_dir = os.path.join(args.out, "fetched")
        os.makedirs(fetched_dir, exist_ok=True)
        for name in (build_oversized_fb2(fetched_dir), build_zipbomb(fetched_dir)):
            p = os.path.join(fetched_dir, name)
            print(f"{os.path.getsize(p):>10}  (not hashed, large)  fetched/{name}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
