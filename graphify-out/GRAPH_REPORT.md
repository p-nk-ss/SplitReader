# Graph Report - .  (2026-05-10)

## Corpus Check
- Corpus is ~6,134 words - fits in a single context window. You may not need a graph.

## Summary
- 212 nodes · 281 edges · 29 communities (11 shown, 18 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 31 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_DI Module & Book Domain|DI Module & Book Domain]]
- [[_COMMUNITY_Presentation & Parser Layer|Presentation & Parser Layer]]
- [[_COMMUNITY_Data & Domain Core|Data & Domain Core]]
- [[_COMMUNITY_Home Screen UI|Home Screen UI]]
- [[_COMMUNITY_Reader Activity UI|Reader Activity UI]]
- [[_COMMUNITY_Language Picker UI|Language Picker UI]]
- [[_COMMUNITY_Reader ViewModel Logic|Reader ViewModel Logic]]
- [[_COMMUNITY_Book Parsing Use Case|Book Parsing Use Case]]
- [[_COMMUNITY_Reading Progress Persistence|Reading Progress Persistence]]
- [[_COMMUNITY_Translation Data & Use Case|Translation Data & Use Case]]
- [[_COMMUNITY_Paragraph List Adapter|Paragraph List Adapter]]
- [[_COMMUNITY_Translation State Machine|Translation State Machine]]
- [[_COMMUNITY_App Launcher Icons (Square)|App Launcher Icons (Square)]]
- [[_COMMUNITY_App Launcher Icons (Round)|App Launcher Icons (Round)]]
- [[_COMMUNITY_Translation Cache DAO|Translation Cache DAO]]
- [[_COMMUNITY_Room Database Module|Room Database Module]]
- [[_COMMUNITY_Home ViewModel Logic|Home ViewModel Logic]]
- [[_COMMUNITY_Gradle Build Config|Gradle Build Config]]
- [[_COMMUNITY_Instrumentation Tests|Instrumentation Tests]]
- [[_COMMUNITY_Entry Point Activity|Entry Point Activity]]
- [[_COMMUNITY_Room AppDatabase|Room AppDatabase]]
- [[_COMMUNITY_Language Detection|Language Detection]]
- [[_COMMUNITY_Language Model|Language Model]]
- [[_COMMUNITY_Book Parser Interface|Book Parser Interface]]
- [[_COMMUNITY_Translation Repository|Translation Repository]]
- [[_COMMUNITY_Unit Tests|Unit Tests]]
- [[_COMMUNITY_Application Class|Application Class]]
- [[_COMMUNITY_Unit Test (Semantic)|Unit Test (Semantic)]]

## God Nodes (most connected - your core abstractions)
1. `ReaderActivity` - 12 edges
2. `ReaderViewModel` - 10 edges
3. `ReadingProgressManager` - 8 edges
4. `ParagraphAdapter` - 7 edges
5. `ReaderViewModel` - 7 edges
6. `App Launcher Icon (hdpi)` - 7 edges
7. `App Launcher Icon (mdpi)` - 7 edges
8. `App Launcher Icon (xhdpi)` - 7 edges
9. `App Launcher Icon (xxhdpi)` - 7 edges
10. `App Launcher Icon (xxxhdpi)` - 7 edges

## Surprising Connections (you probably didn't know these)
- `TranslateTextUseCase` --semantically_similar_to--> `TranslationRepository Interface`  [INFERRED] [semantically similar]
  app/src/main/java/com/example/splitreader/domain/usecase/TranslateTextUseCase.kt → app/src/main/java/com/example/splitreader/domain/repository/TranslationRepository.kt
- `LanguageDetector` --conceptually_related_to--> `TranslationRepositoryImpl`  [INFERRED]
  app/src/main/java/com/example/splitreader/domain/LanguageDetector.kt → app/src/main/java/com/example/splitreader/data/repository/TranslationRepositoryImpl.kt
- `ParseBookUseCase.ParseResult` --semantically_similar_to--> `TranslationState Sealed Class`  [INFERRED] [semantically similar]
  app/src/main/java/com/example/splitreader/domain/usecase/ParseBookUseCase.kt → app/src/main/java/com/example/splitreader/domain/model/TranslationState.kt
- `EpubParser` --semantically_similar_to--> `Fb2Parser`  [INFERRED] [semantically similar]
  app/src/main/java/com/example/splitreader/domain/parser/EpubParser.kt → app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt
- `HomeViewModel` --semantically_similar_to--> `ReaderViewModel`  [INFERRED] [semantically similar]
  app/src/main/java/com/example/splitreader/presentation/home/HomeViewModel.kt → app/src/main/java/com/example/splitreader/presentation/reader/ReaderViewModel.kt

## Hyperedges (group relationships)
- **Hilt Dependency Injection System** — splitreaderapplication_splitreaderapplication, mainactivity_mainactivity, appmodule_appmodule, databasemodule_databasemodule, translationrepositoryimpl_translationrepositoryimpl, readingprogressmanager_readingprogressmanager, languagedetector_languagedetector [EXTRACTED 1.00]
- **Room Database Layer** — appdatabase_appdatabase, translationdao_translationdao, translationcacheentity_translationcacheentity, databasemodule_databasemodule [EXTRACTED 1.00]
- **Translation Pipeline** — translationrepositoryimpl_translationrepositoryimpl, translationdao_translationdao, translationcacheentity_translationcacheentity, domain_repository_translationrepository, domain_model_language [EXTRACTED 1.00]
- **Domain Model Layer** — book_book, chapter_chapter, domain_model_language [EXTRACTED 1.00]
- **Gradle Build System** — build_gradle_kts_root, settings_gradle_kts, app_build_gradle_kts [EXTRACTED 1.00]
- **Book Parsing Flow** — ParseBookUseCase_ParseBookUseCase, EpubParser_EpubParser, Fb2Parser_Fb2Parser, BookParser_BookParser [EXTRACTED 1.00]
- **Translation Pipeline (Domain)** — Language_Language, Language_toTranslateLanguage, TranslateTextUseCase_TranslateTextUseCase, TranslationState_TranslationState [EXTRACTED 1.00]
- **Reader Screen MVVM** — ReaderActivity_ReaderActivity, ReaderViewModel_ReaderViewModel, ParagraphAdapter_ParagraphAdapter, LanguagePickerDialog_LanguagePickerDialog [EXTRACTED 1.00]
- **Home Screen MVVM** — HomeFragment_HomeFragment, HomeViewModel_HomeViewModel, RecentBooksAdapter_RecentBooksAdapter [EXTRACTED 1.00]
- **End-to-End Read and Translate Flow** — HomeFragment_HomeFragment, HomeViewModel_HomeViewModel, ParseBookUseCase_ParseBookUseCase, ReaderActivity_ReaderActivity, ReaderViewModel_ReaderViewModel, TranslateTextUseCase_TranslateTextUseCase, TranslationState_TranslationState, ParagraphAdapter_ParagraphAdapter [INFERRED 0.95]

## Communities (29 total, 18 thin omitted)

### Community 0 - "DI Module & Book Domain"
Cohesion: 0.14
Nodes (6): AppModule, Book, Chapter, EpubParser, OpfData, Fb2Parser

### Community 1 - "Presentation & Parser Layer"
Cohesion: 0.2
Nodes (18): BookParser Interface, EpubParser, Fb2Parser, HomeFragment, HomeViewModel, LanguagePickerDialog.LanguageAdapter, LanguagePickerDialog, Language Enum (+10 more)

### Community 2 - "Data & Domain Core"
Cohesion: 0.17
Nodes (17): AppDatabase, AppModule (Hilt DI Module), Book (Domain Model), Chapter (Domain Model), DatabaseModule (Hilt DI Module), Language (Domain Model Enum), EpubParser, Fb2Parser (+9 more)

### Community 3 - "Home Screen UI"
Cohesion: 0.2
Nodes (3): HomeFragment, RecentBooksAdapter, ViewHolder

### Community 5 - "Language Picker UI"
Cohesion: 0.22
Nodes (4): LanguageAdapter, LanguagePickerDialog, newInstance(), ViewHolder

### Community 7 - "Book Parsing Use Case"
Cohesion: 0.29
Nodes (6): Error, Idle, Loading, ParseBookUseCase, ParseResult, Success

### Community 9 - "Translation Data & Use Case"
Cohesion: 0.25
Nodes (3): TranslationCacheEntity, TranslationRepositoryImpl, TranslateTextUseCase

### Community 11 - "Translation State Machine"
Cohesion: 0.25
Nodes (7): DownloadingModel, Error, Idle, Partial, Success, Translating, TranslationState

### Community 12 - "App Launcher Icons (Square)"
Cohesion: 0.96
Nodes (8): Android Launcher Icon Concept, Android Robot / Default Placeholder Icon, Material Design Long Shadow Icon Style, App Launcher Icon (hdpi), App Launcher Icon (mdpi), App Launcher Icon (xhdpi), App Launcher Icon (xxhdpi), App Launcher Icon (xxxhdpi)

### Community 13 - "App Launcher Icons (Round)"
Cohesion: 0.93
Nodes (8): Android Adaptive Screen Density (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi), Default Android Robot Icon (Green, Grid Background), Android Round Launcher Icon Asset, Round Launcher Icon (hdpi), Round Launcher Icon (mdpi), Round Launcher Icon (xhdpi), Round Launcher Icon (xxhdpi), Round Launcher Icon (xxxhdpi)

## Knowledge Gaps
- **18 isolated node(s):** `SplitReaderApplication`, `Language`, `TranslationState`, `Idle`, `DownloadingModel` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ParagraphAdapter` connect `Paragraph List Adapter` to `Reader Activity UI`?**
  _High betweenness centrality (0.006) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `ReaderViewModel` (e.g. with `LanguagePickerDialog` and `HomeViewModel`) actually correct?**
  _`ReaderViewModel` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `SplitReaderApplication`, `Language`, `TranslationState` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `DI Module & Book Domain` be split into smaller, more focused modules?**
  _Cohesion score 0.14 - nodes in this community are weakly interconnected._