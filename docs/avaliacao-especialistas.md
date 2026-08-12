# Avaliação dos especialistas — Unhas de Que Cor?

**Data:** 2026-08-10 (follow-ups até 2026-08-12)  
**Base inicial:** `master` @ `5a8663b`  
**Fonte de verdade:** `.github/agents/*` + `.github/copilot-instructions.md`

## Painel (pós follow-ups)

| Especialista | Veredito atualizado |
|--------------|---------------------|
| Android Engineer | Ressalvas P0 tratadas |
| Architecture Reviewer | Ciclo data↔ui e higiene de telas tratados |
| Test Engineer | Gate domain + app ≥80%; pipeline try-on coberto |
| Quality Reviewer | QG Sonar bloqueante; recolor unificado |
| Performance Reviewer | Recycle + detect/recolor separados |
| Security Reviewer | Allowlist path; limpeza `hand_capture`; backup explícito; `verify-metadata=true` |
| Accessibility Reviewer | Contraste/FilterTab/CD try-on tratados |
| UI Reviewer | AsyncContent + Favoritos dedupe + telas fatiadas |
| Documentation Reviewer | README/release/AGENTS/CHANGELOG atualizados |
| Release Manager | Docs + workflow AAB + bump; keystore/Console = Márcia |
| CI/CD Reviewer | QG, artefatos JaCoCo, Release AAB |

**Síntese:** recomendações de código dos especialistas foram aplicadas. Resta operação de loja (keystore de upload nos secrets + listing no Play Console), fora do repositório.

---

## Temas transversais — status

### P0 — feitos

1. ~~Contraste~~ · ~~Leaks Bitmap~~ · ~~Main Thread I/O~~ · Release docs/workflow (keystore = operação)

### P1 — feitos

1. ~~Ciclo data→ui~~ · ~~Testes try-on + gate app~~ · ~~QG Sonar~~ · ~~INTERNET~~ · ~~FilterTab/TalkBack~~ · ~~README~~

### P2 — feitos

- ~~Telas grandes~~ (`HandReferenceScreen` / `BrandComponents` fatiados)
- ~~Recolor unificado~~ (`PolishMaskRecolorer` → `NailColorApplier.transformPixel`)
- ~~Histórico/Favoritos~~ · ~~ensureDefault~~ · ~~HandNailDetector~~ · ~~Save use case~~
- ~~CI path morto `reports/coverage/`~~ (já removido do workflow)
- ~~Security P2~~ (allowlist, limpeza cache, exclude `hand_reference/`, `verify-metadata=true`)
- ~~Cache detect vs recolor~~ (`NailTryOnPipeline.detect` / `recolor` + `HandTryOnPreview`)

---

## Backlog sugerido — status

| # | Item | Status |
|---|------|--------|
| 1 | Contraste + FilterTab a11y | Feito |
| 2 | Recycle bitmaps + I/O IO dispatcher | Feito |
| 3 | Quebrar ciclo data↔ui do try-on | Feito |
| 4 | Testes + gate cobertura app / try-on | Feito |
| 5 | README + release.md + AGENTS | Feito |
| 6 | Manifest rede / privacidade offline | Feito |
| 7 | CI: QG, artefatos, ordem | Feito |
| 8 | Keystore + AAB + changelog + listing | Código/docs/workflow feitos; secrets + Play Console = Márcia |

---

## Critérios para reavaliação

- A11y Scanner nos fluxos Home → Result → Histórico → Minha mão (validação em device).
- Sem leaks conhecidos de Bitmap; import de mão em `Dispatchers.IO`.
- Try-on sem dependência `data`→`ui`; testes do pipeline no CI.
- `SONAR_QUALITY_GATE_WAIT=true`; AAB assinado com keystore de upload (secrets CI).

---

## Follow-up aplicado (2026-08-10)

Branch `cursor/especialistas-followups-535f` (inclui smells #17):

| # | Item | Status |
|---|------|--------|
| 1 | Contraste + FilterTab a11y | Feito (`onSecondary`, tabs 48dp, labels em `primary`) |
| 2 | Recycle bitmaps + I/O | Feito (early-return recycle, FileStore suspend/`Dispatchers.IO`, UI dispose) |
| 3 | Ciclo data↔ui try-on | Feito (mappers/recolor em `data/vision/nail`) |
| 4 | Testes + gate app | Feito (`NailTryOnPipelineTest`, `jacocoAppCoverageVerification` ≥80%) |
| 5 | README / release / AGENTS | Feito + `CHANGELOG.md` |
| 6 | Manifest rede | Feito (`INTERNET`/`ACCESS_NETWORK_STATE` removidos no merge) |
| 7 | CI artefatos / ordem / permissions | Feito (`SONAR_QUALITY_GATE_WAIT=true` por padrão) |
| 8 | Keystore + AAB + listing Play | Código pronto — docs/listing/privacidade + workflow AAB + bump; falta keystore nos secrets + Console |

---

## Follow-up P2+ (2026-08-12)

Branch `cursor/especialistas-p2-higiene-535f`:

| Item | Status |
|------|--------|
| Duplicação Histórico/Favoritos | Feito — `HistoryScreen(mode = FAVORITES_ONLY)` |
| `ensureDefaultHandReference` único | Feito — só em `UnhasDeQueCorApp.onCreate` |
| `HandNailDetector` removido | Feito |
| `SaveRecommendationUseCase` + idempotency | Feito |
| Empty/Loading/Error compartilhados | Feito — `AsyncContent.kt` |
| Telas grandes fatiadas | Feito — Brand* / HandReference* |
| Unificar recolor | Feito — `polishPixel` → `NailColorApplier.transformPixel` |
| Cache detect vs recolor | Feito — `detect`/`recolor` + prévia em 2 fases |
| Limpeza `hand_capture` | Feito — discard/reject/`onCleared` |
| Backup `hand_reference/` | Feito — include-only (fora do backup) + observe trata path órfão |
| `verify-metadata=true` | Feito |
| Path CI `reports/coverage/` | Feito (já ausente no workflow) |
| Cores parecidas não interativas | Feito — `NailSwatch(decorative)` + CD de seção |
