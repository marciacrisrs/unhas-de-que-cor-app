# Changelog

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
