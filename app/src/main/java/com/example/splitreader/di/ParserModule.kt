package com.example.splitreader.di

import com.example.splitreader.domain.parser.BookParser
import com.example.splitreader.domain.parser.EpubParser
import com.example.splitreader.domain.parser.Fb2Parser
import com.example.splitreader.domain.parser.MobiParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Collects every [BookParser] into a `Set<BookParser>` registry. `ParseBookUseCase` picks the
 * matching parser via `canParse`, so adding a format is a new parser class plus one line here —
 * no dispatcher changes. Future: + HuffMobiParser (HUFF/CDIC), + Kf8Parser (AZW3), + PdfParser, …
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds @IntoSet
    abstract fun bindEpubParser(parser: EpubParser): BookParser

    @Binds @IntoSet
    abstract fun bindFb2Parser(parser: Fb2Parser): BookParser

    @Binds @IntoSet
    abstract fun bindMobiParser(parser: MobiParser): BookParser
}
