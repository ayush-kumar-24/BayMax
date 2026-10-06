package com.ayush.baymax.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ayush.baymax.R

/** Nunito variable font bundled in res/font (SIL OFL, see design/NUNITO_OFL.txt). Android-only file. */
@OptIn(ExperimentalTextApi::class)
val Nunito: FontFamily = FontFamily(
    listOf(400, 600, 700, 800).map { w ->
        Font(
            resId = R.font.nunito,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)
