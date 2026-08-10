# Unhas de Que Cor?

Assistente de estilo para unhas: transforma a dúvida “que cor eu passo?” em uma escolha simples, divertida e personalizada.

## Visão

Menos dúvida. Mais unha bonita.

O app recomenda cores considerando ocasião, humor e estilo — ou escolhe por você. Não existe cor certa ou errada: existe uma sugestão que combina com aquele momento.

## Fase 1 — Fundação (MVP)

- Identidade visual e design system (tema claro + escuro)
- Navegação com bottom bar + CTA central
- Tela inicial
- Escolha por contexto
- Escolha por mim
- Preferências de estilo
- Motor inicial de recomendação
- Tela de resultado visual
- Histórico e favoritos locais (Room + DataStore)
- Room com schema exportado e migrações versionadas (sem wipe destrutivo)
- Result idempotente (SavedStateHandle + `idempotencyKey` no histórico)
- Histórico/Favoritos abrem Result em modo restore (sem novo save)
- Room com migrações versionadas + Result idempotente
- Release com R8/minify (ver `docs/release.md`)

## Identidade visual

Paleta (contraste em pontos estratégicos):

| Token | Hex | Uso |
|-------|-----|-----|
| Base | `#FCF1EE` | Fundo |
| Superfície suave | `#ECB2C8` | Cards e áreas calmas |
| Diversão | `#F590B6` | Tags e acentos |
| Ação | `#A4082B` | CTAs e FAB |
| Identidade | `#400113` | Texto forte / marca |

Princípios de UI: cards grandes e arredondados, muito respiro no `#FCF1EE`, ilustração de unhas na recomendação. Tipografia: **Playfair Display** (títulos) + **Poppins** (UI).

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Clean Architecture + MVVM + UDF
- Hilt, Navigation, Room, DataStore
- Testes de domínio com JUnit / MockK / Truth

## Como rodar

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requer Android SDK (compileSdk 36) e JDK 17+.

## CI / qualidade

O mesmo conjunto do GitHub Actions:

```bash
./gradlew verifyCi
```

Isso executa, em sequência:

1. **Detekt** (`:app:detekt`) — análise estática Kotlin  
2. **Android Lint** (`:app:lintDebug`)  
3. **Testes unitários** (`:app:testDebugUnitTest`)  
4. **Cobertura do domínio** (`:app:jacocoDomainCoverageVerification`) — ≥80% linhas  
5. **Assemble debug + release**

Relatórios HTML locais:
- Domain: `app/build/reports/jacoco/jacocoDomainReport/html/index.html`
- App (Sonar): `app/build/reports/jacoco/jacocoAppReport/html/index.html`

### SonarCloud

Com token configurado:

```bash
./gradlew sonar
```

No CI o passo Sonar só roda se o secret `SONAR_TOKEN` existir.  
Setup completo: [`docs/sonar.md`](docs/sonar.md).  
Avaliação dos especialistas (painel + backlog): [`docs/avaliacao-especialistas.md`](docs/avaliacao-especialistas.md).

Configurações em `config/detekt/detekt.yml` e `config/lint/lint.xml`.  
Workflow: `.github/workflows/ci.yml` (roda em qualquer PR e em push para `master`/`main`).

Para atualizar o baseline do Detekt após dívida conhecida:

```bash
./gradlew :app:detektBaseline
```

## Princípios

| Princípio | Decisão |
|-----------|---------|
| Simples | Escolher precisa ser rápido |
| Pessoal | A recomendação considera a pessoa |
| Visual | Cor precisa ser vista, não só descrita |
| Divertido | “Escolha por mim” faz parte da personalidade |
| Sem julgamento | Não existe escolha errada |
