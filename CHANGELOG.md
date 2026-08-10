# Changelog

## 1.0.0 — em preparação

### App
- Recomendação de cores por contexto / “por mim” / estilo
- Histórico e favoritos locais (Room)
- Cadastro de mão (câmera, galeria ou amostras) + try-on na foto
- Temas claro e escuro; identidade Playfair + Poppins

### Qualidade
- CI: Detekt, Lint, unit tests, JaCoCo domain/app, assemble debug/release
- SonarCloud via Gradle (QG opcional via `SONAR_QUALITY_GATE_WAIT`)
- R8/minify no release

### Notas de loja (quando publicar)
- App offline (sem permissão INTERNET no manifesto mesclado)
- Câmera opcional; foto da mão fica no armazenamento local do app
