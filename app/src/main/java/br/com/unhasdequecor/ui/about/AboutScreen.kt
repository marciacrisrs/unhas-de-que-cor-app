package br.com.unhasdequecor.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.BuildConfig
import br.com.unhasdequecor.ui.components.BrandLogoLockup
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

private const val GITHUB_URL = "https://github.com/marciacrisrs/unhas-de-que-cor-app"
private const val GITHUB_LABEL = "github.com/marciacrisrs/unhas-de-que-cor-app"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val versionLabel = "Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Sobre", style = MaterialTheme.typography.headlineSmall) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            },
            actions = {
                NailPolishMark(
                    modifier = Modifier.padding(end = 12.dp),
                    markSize = 40.dp,
                    decorative = true,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            BrandLogoLockup(height = 96.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Menos dúvida. Mais unha bonita.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = versionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = versionLabel
                },
            )
            Spacer(modifier = Modifier.height(24.dp))

            AboutSection(
                title = "O que é",
                body = "Unhas de Que Cor? é um assistente de estilo offline que transforma " +
                    "a dúvida “que cor eu passo?” em uma sugestão simples para o seu momento — " +
                    "por ocasião e humor, ou escolhida por você.",
            )
            Spacer(modifier = Modifier.height(16.dp))
            AboutSection(
                title = "No aparelho",
                body = "Recomendações, histórico, favoritos e a prévia na sua mão rodam " +
                    "localmente. Não enviamos suas fotos nem preferências para nossos servidores.",
            )
            Spacer(modifier = Modifier.height(16.dp))
            AboutSection(
                title = "O que você encontra",
                body = "• Sugestão por contexto ou “escolhe por mim”\n" +
                    "• Preferências de estilo\n" +
                    "• Cadastro da sua mão (ou amostras) para try-on\n" +
                    "• Histórico e favoritos salvos neste aparelho",
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = SoftSurfaceShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Código e projeto",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "O app é open source. Veja o código, issues e novidades no GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Abrir repositório no GitHub"
                        },
                    ) {
                        Text(GITHUB_LABEL)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dúvidas de privacidade: use o contato da política publicada na Play Store.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Feito com carinho para te ajudar a escolher a cor do dia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
    body: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
