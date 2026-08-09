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

## Princípios

| Princípio | Decisão |
|-----------|---------|
| Simples | Escolher precisa ser rápido |
| Pessoal | A recomendação considera a pessoa |
| Visual | Cor precisa ser vista, não só descrita |
| Divertido | “Escolha por mim” faz parte da personalidade |
| Sem julgamento | Não existe escolha errada |
