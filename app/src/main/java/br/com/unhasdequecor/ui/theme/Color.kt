package br.com.unhasdequecor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta oficial — contraste em pontos estratégicos.
 *
 * #FCF1EE base · #ECB2C8 superfícies · #F590B6 diversão ·
 * #A4082B ações · #400113 texto / identidade
 */
val BrandBase = Color(0xFFFCF1EE)
val BrandSoftSurface = Color(0xFFECB2C8)
val BrandFun = Color(0xFFF590B6)
val BrandAction = Color(0xFFA4082B)
val BrandInk = Color(0xFF400113)

val BrandOnAction = BrandBase
val BrandCard = Color(0xFFFFF8F6)
val BrandOutline = Color(0xFFE5B7C4)

// Dark — mesma identidade, profundidade vinho
val DarkBase = Color(0xFF2A0010)
val DarkSoftSurface = Color(0xFF4A1830)
val DarkFun = Color(0xFFF590B6)
val DarkAction = Color(0xFFE45C8B)
val DarkInk = Color(0xFFFCF1EE)
val DarkCard = Color(0xFF3A1024)
val DarkOutline = Color(0xFF6B3A4E)
val DarkOnAction = Color(0xFF2A0010)

// Aliases legados usados em telas/preview
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
