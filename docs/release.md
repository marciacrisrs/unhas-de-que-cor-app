# Release checklist — Unhas de Que Cor?

## Antes do Play Console

1. **Keystore de release**
   - Gere um keystore (guarde backup offline).
   - Configure em `~/.gradle/gradle.properties` ou secrets de CI:

   ```properties
   RELEASE_STORE_FILE=/caminho/upload-keystore.jks
   RELEASE_STORE_PASSWORD=...
   RELEASE_KEY_ALIAS=...
   RELEASE_KEY_PASSWORD=...
   ```

   Sem essas props, `assembleRelease` usa assinatura **debug** (só para validar minify).

2. **Build**

   ```bash
   ./gradlew verifyCi
   ./gradlew :app:bundleRelease   # AAB para Play
   # ou
   ./gradlew :app:assembleRelease # APK
   ```

   - APK: `app/build/outputs/apk/release/app-release.apk`
   - AAB: `app/build/outputs/bundle/release/app-release.aab`

3. **Validar upgrade de Room**
   - Instale build antigo (schema v1) → atualize para o release.
   - Confirme histórico/favoritos intactos (migração 1→2).

4. **Smoke manual**
   - Home → contexto → Result (try-on na foto)
   - Escolha por mim → Result
   - Minha mão: amostra, galeria e câmera; confirmação e remoção
   - Favoritar / compartilhar
   - Histórico e Favoritos abrem Result (restore, sem novo save)
   - Tema claro/escuro
   - TalkBack: CTAs, FilterTabs, prévia try-on, FAB

5. **Store listing**
   - Ícone 512, feature graphic, screenshots
   - Política de privacidade (app offline; câmera opcional; dados locais; backup Android — foto da mão fora do backup)
   - Classificação de conteúdo
   - Changelog / “O que há de novo”: ver `CHANGELOG.md`

## Gates de qualidade

```bash
./gradlew verifyCi
```

Inclui: Detekt + Lint + unit tests + JaCoCo domain ≥80% + JaCoCo app ≥80% + assembleDebug + assembleRelease (R8/minify).

Sonar: `./gradlew sonar` (QG bloqueante com `SONAR_QUALITY_GATE_WAIT=true`).
