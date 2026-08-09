package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.unhasdequecor.R
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.ui.theme.BrandAction
import br.com.unhasdequecor.ui.theme.BrandFun
import br.com.unhasdequecor.ui.theme.FunChipShape
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import br.com.unhasdequecor.ui.theme.UnhasDeQueCorTheme

/**
 * Mark compacto para toolbars/listas: frasco + anel quebrado + sparkles em chip circular.
 * O lockup oficial completo fica em [BrandLogoLockup] / [BrandHeader] (Home/Perfil).
 */
@Composable
fun NailPolishMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 48.dp,
    polishColor: Color? = null,
    decorative: Boolean = false,
) {
    val resolvedPolish = polishColor ?: BrandFun
    val outline = MaterialTheme.colorScheme.onBackground
    val framed = polishColor == null
    val markModifier = if (decorative) {
        modifier.size(markSize).clearAndSetSemantics { }
    } else {
        modifier
            .size(markSize)
            .semantics { contentDescription = "Ícone do app Unhas de Que Cor" }
    }

    Box(
        modifier = markModifier.then(
            if (framed) {
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                        shape = CircleShape,
                    )
                    .padding(4.dp)
            } else {
                Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTintedPolishMark(polishColor = resolvedPolish, outline = outline)
        }
    }
}

private fun DrawScope.drawTintedPolishMark(
    polishColor: Color,
    outline: Color,
) {
    val stroke = Stroke(width = size.minDimension * 0.04f, cap = StrokeCap.Round)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.42f
    val ring = Brush.sweepGradient(listOf(BrandFun, BrandAction, BrandFun))
    val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
    val topLeft = Offset(cx - radius, cy - radius)

    drawArc(
        brush = ring,
        startAngle = -35f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )
    drawArc(
        color = outline.copy(alpha = 0.45f),
        startAngle = 230f,
        sweepAngle = 50f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )

    val bottleTop = cy - radius * 0.55f
    val bottleBottom = cy + radius * 0.52f
    val bottleHalf = radius * 0.28f
    val bottlePath = Path().apply {
        moveTo(cx - bottleHalf * 0.55f, bottleTop)
        lineTo(cx + bottleHalf * 0.55f, bottleTop)
        lineTo(cx + bottleHalf * 0.7f, bottleTop + radius * 0.2f)
        lineTo(cx + bottleHalf, bottleTop + radius * 0.28f)
        lineTo(cx + bottleHalf, bottleBottom)
        quadraticTo(cx, bottleBottom + radius * 0.12f, cx - bottleHalf, bottleBottom)
        lineTo(cx - bottleHalf, bottleTop + radius * 0.28f)
        lineTo(cx - bottleHalf * 0.7f, bottleTop + radius * 0.2f)
        close()
    }
    drawPath(
        path = bottlePath,
        brush = Brush.verticalGradient(
            colors = listOf(polishColor.copy(alpha = 0.55f), polishColor),
            startY = bottleTop,
            endY = bottleBottom,
        ),
    )
    drawPath(path = bottlePath, color = outline, style = stroke)
    drawMarkSparkles(cx = cx, cy = cy, radius = radius)
}

private fun DrawScope.drawMarkSparkles(
    cx: Float,
    cy: Float,
    radius: Float,
) {
    listOf(
        Offset(cx + radius * 0.55f, cy - radius * 0.35f) to radius * 0.11f,
        Offset(cx - radius * 0.58f, cy + radius * 0.08f) to radius * 0.08f,
        Offset(cx + radius * 0.18f, cy + radius * 0.52f) to radius * 0.07f,
        Offset(cx - radius * 0.18f, cy - radius * 0.52f) to radius * 0.06f,
    ).forEach { (center, s) ->
        val sparklePath = Path().apply {
            moveTo(center.x, center.y - s)
            lineTo(center.x + s * 0.22f, center.y - s * 0.22f)
            lineTo(center.x + s, center.y)
            lineTo(center.x + s * 0.22f, center.y + s * 0.22f)
            lineTo(center.x, center.y + s)
            lineTo(center.x - s * 0.22f, center.y + s * 0.22f)
            lineTo(center.x - s, center.y)
            lineTo(center.x - s * 0.22f, center.y - s * 0.22f)
            close()
        }
        drawPath(path = sparklePath, color = BrandFun)
    }
}

@Composable
fun BrandLogoLockup(
    modifier: Modifier = Modifier,
    contentDescription: String = "Unhas de Que Cor?",
) {
    Image(
        painter = painterResource(R.drawable.logo_horizontal),
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .height(128.dp),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Hero da marca: usa o lockup oficial (PNG/WebP) em vez de recompor tipografia.
 * Claro/escuro via `drawable` / `drawable-night`.
 */
@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
) {
    // O asset oficial já inclui tipografia + tagline; showTagline mantém API estável.
    BrandLogoLockup(
        modifier = modifier.padding(horizontal = 8.dp),
        contentDescription = if (showTagline) {
            "Unhas de Que Cor? Sua cor, seu estilo, seu momento"
        } else {
            "Unhas de Que Cor?"
        },
    )
}

@Composable
fun PrimaryCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = SoftSurfaceShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
        Box(modifier = Modifier.width(8.dp))
        Text(text = text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = SoftSurfaceShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text = text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StyleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .clip(FunChipShape)
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = FunChipShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                role = Role.Checkbox
                contentDescription = if (selected) "$label selecionado" else label
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
            Box(modifier = Modifier.width(6.dp))
        }
        Text(text = label, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun NailSwatch(
    colorHex: Long,
    modifier: Modifier = Modifier,
    width: Dp = 36.dp,
    height: Dp = 56.dp,
    colorName: String? = null,
) {
    val description = colorName?.let { "Amostra da cor $it" } ?: "Amostra da cor"
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color(colorHex))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(50),
            )
            .semantics { contentDescription = description },
    )
}

@Composable
fun InfoTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .clip(FunChipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), FunChipShape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
fun FilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = FunChipShape
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leadingIcon?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                Color.White
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )
    }
}

@Composable
fun HistoryRow(
    colorName: String,
    colorHex: Long,
    tags: List<NailStyle>,
    dateLabel: String,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(SoftSurfaceShape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NailSwatch(colorHex = colorHex, colorName = colorName)
            Box(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = colorName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tags.joinToString(" • ") { it.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.semantics {
                    contentDescription = if (isFavorite) {
                        "Remover $colorName dos favoritos"
                    } else {
                        "Salvar $colorName nos favoritos"
                    }
                },
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun ProgressSteps(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
        Box(modifier = Modifier.width(8.dp))
        Text(
            text = "$current/$total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrandHeaderPreview() {
    UnhasDeQueCorTheme {
        BrandHeader(modifier = Modifier.padding(16.dp))
    }
}
