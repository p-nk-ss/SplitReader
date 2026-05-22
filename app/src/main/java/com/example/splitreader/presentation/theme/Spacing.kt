package com.example.splitreader.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 4 dp grid. Use Spacing.md, etc. throughout — avoid raw dp literals in
// screens except where the design specifies an off-scale value.
@Immutable
data class Spacing(
    val xxs: Dp = 4.dp,
    val xs:  Dp = 8.dp,
    val sm:  Dp = 12.dp,
    val md:  Dp = 16.dp,
    val lg:  Dp = 22.dp,
    val xl:  Dp = 28.dp,
    val xxl: Dp = 36.dp,
    val huge:Dp = 56.dp,
    // Reader page margins (per page, not per spread)
    val pageOuter:  Dp = 56.dp, // gutter side
    val pageInner:  Dp = 64.dp, // outer edge
    // Outer chrome
    val railWidth:  Dp = 88.dp,
    val statusBar:  Dp = 30.dp,
)

@Immutable
data class Radii(
    val sm: Dp = 6.dp,
    val md: Dp = 10.dp,
    val lg: Dp = 14.dp,
    val xl: Dp = 18.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadii   = staticCompositionLocalOf { Radii() }
