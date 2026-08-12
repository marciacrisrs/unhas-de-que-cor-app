package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.R
import br.com.unhasdequecor.ui.theme.UnhasDeQueCorTheme

@Composable
fun BrandLogoLockup(
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    contentDescription: String = "Unhas de Que Cor?",
) {
    Image(
        painter = painterResource(R.drawable.logo_horizontal),
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
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
    lockupHeight: Dp = 72.dp,
) {
    // O asset oficial já inclui tipografia + tagline; showTagline mantém API estável.
    BrandLogoLockup(
        modifier = modifier.padding(horizontal = 4.dp),
        height = lockupHeight,
        contentDescription = if (showTagline) {
            "Unhas de Que Cor? Sua cor, seu estilo, seu momento"
        } else {
            "Unhas de Que Cor?"
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BrandHeaderPreview() {
    UnhasDeQueCorTheme {
        BrandHeader(modifier = Modifier.padding(16.dp))
    }
}
