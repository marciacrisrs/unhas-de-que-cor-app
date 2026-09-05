# Changelog

## 1.0.12 — (versionCode 13)

### Correções
- Live Try-On: recorte da máscara na borda do frame (sem crash/`getPixels` OOB)
- Foto da mão: save serializado e persistência confirmada sobrevive se a tela for fechada
- Resultado: a mesma sessão não sorteia outra cor após morte do processo
- Favoritos: cores salvas pelo coração aparecem mesmo sem linha no histórico
- Meu estilo: toques rápidos nos chips não perdem seleção

### Unha humana / Live
- Tracker esquece placa quando o dedo some; geometria inválida e predição fora do quadro desaparecem em vez de pintar pele
- Almond mais estreito, cutícula mais curta, unha curta squoval; segmenter não restaura almond cheio na pele
- Pipeline de foto não pinta elipse quando a máscara falha
- Resultado → Minha mão volta ao Resultado (não à Home)
- Aba Favoritos com chrome de aba (coração), distinto do Histórico

### Qualidade
- Especialista de anatomia da unha (`human-nail-anatomy-reviewer`) separado de visão e de render
- `keystore.b64` ignorado no git

## 1.0.11 — (versionCode 12)

### Try-on
- Entrada navegável de Live Try-On (câmera)
- Crash ao sair do Live ou sem câmera frontal
- Inferência Live mais barata; métricas de pipeline

### Acessibilidade / release
- Auditoria de a11y (chips 48dp, anúncio de loading/erro)
- Checklist de release do MVP

## 1.0.8 — (versionCode 9)

### Try-on / visão
- Floor de confiança centralizado (`DetectionConfidenceFloor`)
- Detecção mais robusta em luz difícil (flash, contraluz, tip-glare)
- Feedback tipado de falha (`DetectionFailureReason`) com mensagens amigáveis
- CTA “Tentar outra foto” quando a prévia é aproximada ou não detectada
- Claim FULL só com presence forte, tip-span aberto e ≥3 unhas de qualidade

### Qualidade
- Matriz de testes de visão documentada (`docs/vision-test-matrix.md`)
- Checklist de device real (`docs/device-testing.md`)
- Keystores ignorados no git

## 1.0.1 — release Play (versionCode 2)

- Mesmo conteúdo da 1.0.0; bump obrigatório porque o `versionCode 1` já foi usado na Play.

## 1.0.0 — em preparação

### App
- Recomendação de cores por contexto / “por mim” / estilo
- Histórico e favoritos locais (Room)
- Cadastro de mão (câmera, galeria ou amostras) + try-on na foto
- Temas claro e escuro; identidade Playfair + Poppins

### Qualidade
- CI: Detekt, Lint, unit tests, JaCoCo domain/app, assemble debug/release
- SonarCloud via Gradle com Quality Gate bloqueante (`SONAR_QUALITY_GATE_WAIT=true`)
- R8/minify no release; workflow `Release AAB` automático em tags `v*` (+ dispatch manual)

### Notas de loja (quando publicar)
- App offline (sem permissão INTERNET no manifesto mesclado)
- Câmera opcional; foto da mão fica no armazenamento local do app
- Textos: `docs/play-listing.md` · Privacidade: `docs/privacy-policy.md`
