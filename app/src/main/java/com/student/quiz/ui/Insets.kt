package com.student.quiz.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Window insets for the edge-to-edge window (MainActivity calls
 * enableEdgeToEdge). System bars plus the display cutout, so nothing a student
 * needs to read or tap sits under the status bar, the navigation bar or a
 * notch, on any device. The insets are only read inside a composition, because
 * the system bars can appear and disappear while the app runs.
 */
private val allSides: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

private val statusSides: WindowInsets
    @Composable get() = allSides.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

private val navigationSides: WindowInsets
    @Composable get() = allSides.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

/** All four edges: for whole screens (join, lobby, scan, ended). */
@Composable
fun Modifier.systemBarPadding(): Modifier = windowInsetsPadding(allSides)

/** Top and sides only: for content above a bottom-anchored row. */
@Composable
fun Modifier.statusBarPaddingOnly(): Modifier = windowInsetsPadding(statusSides)

/** Bottom and sides: keeps the navigation row clear of the gesture/nav bar. */
@Composable
fun Modifier.navigationBarPaddingOnly(): Modifier = windowInsetsPadding(navigationSides)