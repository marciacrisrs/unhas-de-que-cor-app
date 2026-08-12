# Play Console — textos e assets (1.0.0)

Use com [`docs/release.md`](release.md) e [`docs/privacy-policy.md`](privacy-policy.md).

## Identidade

| Campo | Valor |
|-------|--------|
| Nome | Unhas de Que Cor? |
| Application ID | `br.com.unhasdequecor` |
| Idioma padrão | Português (Brasil) |
| Categoria sugerida | Estilo de vida / Beleza |

## Descrição curta (≤80 caracteres)

```text
Descubra a cor de unha do momento — com try-on na sua foto.
```

## Descrição completa

```text
Menos dúvida. Mais unha bonita.

O Unhas de Que Cor? transforma o “que cor eu passo?” em uma escolha simples e divertida. Informe o contexto (ocasião, humor, estilo) — ou deixe o app escolher por você — e receba uma sugestão personalizada.

Destaques:
• Recomendação por contexto ou “escolhe por mim”
• Try-on na foto real da sua mão (offline, no aparelho)
• Cadastre sua mão pela câmera, galeria ou amostras
• Histórico e favoritos salvos só no seu celular
• Temas claro e escuro

Privacidade: o app é offline. Fotos e preferências ficam no dispositivo; a foto da mão não entra no backup automático do Android.

Não existe cor certa ou errada — existe a sugestão que combina com aquele momento.
```

## O que há de novo (1.0.0)

```text
Primeira versão na Play Store:
• Recomendação de cores por contexto ou por mim
• Try-on na foto da mão
• Histórico e favoritos locais
```

(Detalhe técnico: ver `CHANGELOG.md`.)

## Classificação de conteúdo

- Sem violência, conteúdo sexual, drogas ou linguagem ofensiva
- Questionário IARC: responder conforme o app (geralmente “Todos” / PEGI 3 / similar)

## Política de privacidade

1. Publique `docs/privacy-policy.md` em URL HTTPS pública (contato: marciacrisrs@gmail.com).
2. Cole a URL em Play Console → Política do app → Privacidade.

## Assets (checklist)

| Asset | Spec | Status |
|-------|------|--------|
| Ícone hi-res | 512×512 PNG | [ ] gerar a partir do launcher |
| Feature graphic | 1024×500 | [ ] |
| Screenshots phone | ≥2 (ideal 4–8), sem notch mock obrigatório | [ ] Home, Result+try-on, Minha mão, Histórico |
| Screenshots tablet | opcional | [ ] |

Sugestão de ordem nas screenshots: Home → Result com try-on → Minha mão → Favoritos/Histórico.

## Conta e assinatura

1. Criar app no Play Console (ou usar existente).
2. Ativar **Play App Signing** (recomendado): envie o upload key / deixe o Google gerenciar a key de assinatura.
3. Gerar upload keystore local: `scripts/generate-upload-keystore.sh`
4. Configurar `RELEASE_*` (ver `docs/release.md`) e gerar AAB: `./gradlew :app:bundleRelease`
5. Internal testing → closed → production quando o smoke passar.
