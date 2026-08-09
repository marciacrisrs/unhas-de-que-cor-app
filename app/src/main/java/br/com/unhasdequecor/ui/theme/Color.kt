package br.com.unhasdequecor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta das imagens guia + identidade reforçada.
 *
 * Claro (board + reforço):
 * #FCF1EE base · #ECB2C8 superfícies · #F590B6 diversão ·
 * #A4082B ações · #400113 texto / identidade
 *
 * Escuro (board TEMA ESCURO):
 * #1A1120 base · #D9468B ação · #FFB6D5 diversão ·
 * #2C1F29 cards · #FCF1EE texto
 */
val BrandBase = Color(0xFFFCF1EE)
val BrandSoftSurface = Color(0xFFECB2C8)
val BrandFun = Color(0xFFF590B6)
val BrandAction = Color(0xFFA4082B)
val BrandInk = Color(0xFF400113)

val BrandOnAction = Color(0xFFFFFFFF)
val BrandCard = Color(0xFFFFFBFA)
val BrandOutline = Color(0xFFE5B7C4)

val DarkBase = Color(0xFF1A1120)
val DarkSoftSurface = Color(0xFF2C1F29)
val DarkFun = Color(0xFFFFB6D5)
val DarkAction = Color(0xFFD9468B)
val DarkInk = Color(0xFFFCF1EE)
val DarkCard = Color(0xFF2C1F29)
val DarkOutline = Color(0xFF4A3340)
val DarkOnAction = Color(0xFFFFFFFF)

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
