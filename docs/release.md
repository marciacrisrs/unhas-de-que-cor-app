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

**Windows** — use barra `/`, path absoluto, **sem aspas**:

```properties
RELEASE_STORE_FILE=C:/Users/marci/keys/unhas-de-que-cor-upload.jks
RELEASE_STORE_PASSWORD=sua_senha
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=sua_senha
```

Sem essas props, `assembleRelease` / `bundleRelease` usam assinatura **debug** (só para validar minify/R8).

### Secrets no GitHub (release automática)

Em **Settings → Secrets and variables → Actions**, crie:

| Secret | Conteúdo |
|--------|----------|
| `RELEASE_KEYSTORE_BASE64` | No PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\marci\keys\unhas-de-que-cor-upload.jks"))` — no bash: `base64 -w0 seu.jks` |
| `RELEASE_STORE_PASSWORD` | senha do store |
| `RELEASE_KEY_ALIAS` | ex. `upload` |
| `RELEASE_KEY_PASSWORD` | senha da key |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON da service account com permissão de release no app (obrigatório para publicar na faixa internal) |

Variable opcional:

| Variable | Default | Uso |
|----------|---------|-----|
| `PLAY_RELEASE_STATUS` | `completed` | `completed` publica direto na faixa internal; use `draft` só se o app ainda estiver em rascunho no Play Console (primeiro upload / listing incompleto) |

### Service account na Play Console

1. [Google Cloud](https://console.cloud.google.com/) → IAM → Service accounts → criar conta → chave JSON.
2. [Play Console](https://play.google.com/console) → **Users and permissions** → convidar o e-mail da service account.
3. Conceder permissão **Release to testing tracks** (e **View app information**) no app `br.com.unhasdequecor`.
4. Colar o JSON inteiro no secret `PLAY_SERVICE_ACCOUNT_JSON`.

### Como dispara

**Manual (recomendado para o primeiro deploy)** — Actions → **Release AAB** → Run workflow (branch `master`).  
Cada execução incrementa `versionCode` (+1) e o patch do `versionName`, gera o AAB assinado, **publica na faixa Teste interno** da Play (`status: completed`) e faz commit da versão no repo.

**Por tag** — a partir do `master`:

```bash
git checkout master
git pull
git tag v1.0.3
git push origin v1.0.3
```

Na tag, o `versionName` vira o nome da tag (sem `v`); o upload para teste interno também roda automaticamente se `PLAY_SERVICE_ACCOUNT_JSON` existir.

Marque *skip_play_upload* no dispatch manual só se quiser gerar o AAB sem enviar à Play.

Sem os secrets `RELEASE_*`, o workflow **falha de propósito** (não publica AAB assinado com debug).  
Sem `PLAY_SERVICE_ACCOUNT_JSON`, o AAB é gerado e fica como artifact, mas **não** sobe à Play.

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

No Windows, se o build falhar em `aapt2-…-windows.jar` com dependency verification, atualize o `gradle/verification-metadata.xml` (o CI Linux só gera o artefato linux por padrão). Use também `--no-configuration-cache` se o cache reclamar:

```bash
gradlew :app:bundleRelease --no-configuration-cache
```

## 3. Play Console (ordem sugerida)

1. Criar app + preencher [`play-listing.md`](play-listing.md)
2. Publicar política de privacidade (URL HTTPS) e colar no Console
3. Ativar **Play App Signing** e registrar o upload key
4. Preencher listing, privacidade, classificação de conteúdo e demais formulários obrigatórios (app fora do status *Draft*)
5. **Release AAB** no GitHub Actions publica direto na faixa **Teste interno** (`PLAY_SERVICE_ACCOUNT_JSON` + keystore)
6. Smoke no device (abaixo) na faixa interna
7. Promover para fechado/produção quando estável

> **Primeiro upload:** se o app ainda estiver *Draft* no Console, defina a variable `PLAY_RELEASE_STATUS=draft`, rode o workflow e finalize o release manualmente no Console uma vez. Depois volte para `completed` (default).

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
