package br.com.unhasdequecor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Identidade oficial (claro) + dark das telas guia.
 *
 * Claro — contraste estratégico:
 * #FCF1EE base · #ECB2C8 superfícies · #F590B6 diversão ·
 * #A4082B ações · #400113 texto / identidade
 *
 * Escuro — mockups guia: base quase preta, rosa vibrante, highlight cream.
 */
val BrandBase = Color(0xFFFCF1EE)
val BrandSoftSurface = Color(0xFFECB2C8)
val BrandFun = Color(0xFFF590B6)
val BrandAction = Color(0xFFA4082B)
val BrandInk = Color(0xFF400113)

val BrandOnAction = BrandBase
val BrandCard = Color(0xFFFFF8F6)
val BrandOutline = Color(0xFFE5B7C4)

// Dark — imagens guia
val DarkBase = Color(0xFF0D0B12)
val DarkSoftSurface = Color(0xFF2C1F29)
val DarkFun = Color(0xFFFFB6D5)
val DarkAction = Color(0xFFE94E89)
val DarkInk = Color(0xFFFCF1EE)
val DarkCard = Color(0xFF1A1120)
val DarkOutline = Color(0xFF4A3340)
val DarkOnAction = Color(0xFFFFFFFF)

// Aliases
val LightBase = BrandBase
val LightPrimary = BrandAction
val LightSecondary = BrandFun
val LightHighlight = BrandInk
val LightNeutral = BrandSoftSurface
val LightOnPrimary = BrandOnAction
val LightSurface = BrandCard
val LightOutline = BrandOutline

val DarkPrimary = DarkAction
val DarkSecondary = DarkFun
val DarkHighlight = DarkInk
val DarkSurface = DarkCard
val DarkOnPrimary = DarkOnAction
