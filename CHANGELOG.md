# Changelog

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
