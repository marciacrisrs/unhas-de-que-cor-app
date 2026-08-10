# Release checklist — Unhas de Que Cor?

Fluxo completo até a Play: **keystore → AAB → listing → internal test → smoke**.

Textos da loja: [`docs/play-listing.md`](play-listing.md)  
Privacidade: [`docs/privacy-policy.md`](privacy-policy.md)

## 1. Keystore de upload

```bash
./scripts/generate-upload-keystore.sh
# ou: ./scripts/generate-upload-keystore.sh ~/keys/unhas-upload.jks upload
```

Configure em `~/.gradle/gradle.properties` (nunca no git):

```properties
RELEASE_STORE_FILE=/caminho/unhas-de-que-cor-upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
```

Sem essas props, `assembleRelease` / `bundleRelease` usam assinatura **debug** (só para validar minify/R8).

### Secrets no GitHub (workflow Release AAB)

| Secret | Conteúdo |
|--------|----------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 seu.jks` |
| `RELEASE_STORE_PASSWORD` | senha do store |
| `RELEASE_KEY_ALIAS` | ex. `upload` |
| `RELEASE_KEY_PASSWORD` | senha da key |

Dispare **Actions → Release AAB → Run workflow**.

## 2. Build local

```bash
./gradlew verifyCi
./gradlew :app:bundleRelease   # AAB para Play
# ou
./gradlew :app:assembleRelease # APK
```

- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

Versão atual: `versionName 1.0.0` / `versionCode 1` (`app/build.gradle.kts`).

## 3. Play Console (ordem sugerida)

1. Criar app + preencher [`play-listing.md`](play-listing.md)
2. Publicar política de privacidade (URL HTTPS) e colar no Console
3. Ativar **Play App Signing** e registrar o upload key
4. Criar release em **Teste interno** com o AAB
5. Smoke no device (abaixo) na faixa interna
6. Promover para fechado/produção quando estável

## 4. Validar upgrade de Room

- Instale build antigo (schema v1) → atualize para o release.
- Confirme histórico/favoritos intactos (migração 1→2).

## 5. Smoke manual

- Home → contexto → Result (try-on na foto)
- Escolha por mim → Result
- Minha mão: amostra, galeria e câmera; confirmação e remoção
- Favoritar / compartilhar
- Histórico e Favoritos abrem Result (restore, sem novo save)
- Tema claro/escuro
- TalkBack: CTAs, FilterTabs, prévia try-on, FAB

## 6. Store listing (resumo)

- Ícone 512, feature graphic, screenshots — checklist em `play-listing.md`
- Changelog / “O que há de novo”: `CHANGELOG.md` + bloco em `play-listing.md`
- Classificação de conteúdo (IARC)

## Gates de qualidade

```bash
./gradlew verifyCi
```

Inclui: Detekt + Lint + unit tests + JaCoCo domain ≥80% + JaCoCo app ≥80% + assembleDebug + assembleRelease (R8/minify).

Sonar: `./gradlew sonar` (QG bloqueante com `SONAR_QUALITY_GATE_WAIT=true`).
