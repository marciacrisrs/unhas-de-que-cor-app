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
   ./gradlew :app:assembleRelease
   ```

   Artefato: `app/build/outputs/apk/release/app-release.apk`  
   (ou AAB com `:app:bundleRelease` quando for publicar).

3. **Validar upgrade de Room**
   - Instale build antigo (schema v1) → atualize para o release.
   - Confirme histórico/favoritos intactos (migração 1→2).

4. **Smoke manual**
   - Home → contexto → Result
   - Escolha por mim → Result
   - Favoritar / compartilhar
   - Histórico e Favoritos abrem Result (restore, sem novo save)
   - Tema claro/escuro
   - TalkBack nos CTAs principais

5. **Store listing**
   - Ícone 512, feature graphic, screenshots
   - Política de privacidade (app offline; dados locais; backup Android)
   - Classificação de conteúdo

## Gates de qualidade

```bash
./gradlew verifyCi
./gradlew :app:assembleRelease
```

- Detekt + Lint + unit tests + JaCoCo domain ≥80% + assembleDebug
- Release: R8/minify + shrink resources ligados
