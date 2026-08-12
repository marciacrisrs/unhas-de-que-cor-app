# Avaliação dos especialistas — Unhas de Que Cor?

**Data:** 2026-08-10  
**Base:** `master` @ `5a8663b` (com contexto da PR #17 smells/cobertura)  
**Fonte de verdade:** `.github/agents/*` + `.github/copilot-instructions.md`

## Painel

| Especialista | Veredito |
|--------------|----------|
| Android Engineer | Aprovado com ressalvas |
| Architecture Reviewer | Aprovado com ressalvas |
| Test Engineer | Gate domain OK; estratégia enviesada |
| Quality Reviewer | Aprovado com ressalvas |
| Performance Reviewer | Risco alto mid/low (bitmap/vision) |
| Security Reviewer | Aprovado com ressalvas |
| Accessibility Reviewer | **Não aprovado** (P0 contraste/TalkBack) |
| UI Reviewer | Parcial — P0 contraste FAB/tabs |
| Documentation Reviewer | Aprovado com ressalvas |
| Release Manager | **Não pronta** para Play |
| CI/CD Reviewer | Não aprovar sem ajustes P1 |

**Síntese:** o núcleo de recomendação (domain → use cases → Room/DataStore → MVVM) está sólido e o CI local passa. A vertical **mão / try-on / vision** concentra dívida de arquitetura, testes, memória e UX. **A11y (contraste)** e **release (keystore/AAB/listing)** bloqueiam loja.

---

## Temas transversais (priorizados)

### P0 — tratar antes de ship / a11y

1. **Contraste:** `secondary` (BrandFun) como texto e branco sobre rosa/FAB — falha WCAG (~2:1). Usar `onSecondary` / `primary` / `onSurfaceVariant`.
2. **Leaks de Bitmap:** early-return em `NailColorApplier.apply` e `PolishMaskRecolorer.recolor` sem `recycle` do `copy`; amostras em `HandReferenceScreen` sem dispose.
3. **Main Thread I/O:** `HandReferenceViewModel` copia URI/asset sem `Dispatchers.IO`.
4. **Release:** keystore release + AAB + changelog + privacidade/listing.

### P1 — arquitetura / qualidade / produto

1. **Ciclo `data` → `ui`:** `NailTryOnPipeline` / MediaPipe importam `ui.components`; UI usa EntryPoint Hilt. Mover tipos de visão para pacote neutro e injetar via ViewModel/porta.
2. **Testes try-on:** pipeline, segmenter, `HandReferenceRepositoryImpl` fora do gate; só domain ≥80% bloqueia CI.
3. **QG Sonar não bloqueia** (`SONAR_QUALITY_GATE_WAIT=false`); `jacocoAppReport` não está no `verifyCi`.
4. **INTERNET transitivo** (MediaPipe/CCT) vs narrativa “offline”.
5. **FilterTab / try-on TalkBack:** estado selecionado, CD de status, loadings nomeados.
6. **README desatualizado** (“ilustração”; omite mão/try-on).

### P2 — higiene

- Telas grandes (`HandReferenceScreen`, `BrandComponents`).
- Duplicação Histórico/Favoritos e `NailColorApplier` ↔ `PolishMaskRecolorer`.
- `ensureDefaultHandReference` triplicado; `HandNailDetector` / `SaveRecommendationUseCase` sem uso prod.
- CI: upload APK/AAB, path morto `reports/coverage/`, ordem Sonar vs assemble.

---

## Pareceres por especialista

### 1. Android Engineer — Aprovado com ressalvas

**Fortes:** domínio puro, MVVM + Flow, try-on off-main no preview, DI Hilt, componentes reutilizáveis.

**P0:** I/O na Main no cadastro de mão; `data`→`ui`; bitmaps sem recycle na UI de mão.

**Ações:** `Dispatchers.IO` no import; extrair vision fora de `ui`; recycle como em `HandTryOnPreview`.

### 2. Architecture Reviewer — Aprovado com ressalvas

**Fortes:** Clean Architecture no núcleo de recomendação.

**P0:** DIP quebrado no try-on; EntryPoint no Compose; ViewModel com `HandReferenceFileStore`/`Context`.

**Ações:** porta de try-on + tipos em `data/vision` (ou módulo); I/O só no repositório; fatiar telas.

### 3. Test Engineer — Gate domain OK

**Medição:** domain ~98% LINE; app report ~81% no escopo; branch ~55%.

**Gaps:** `NailTryOnPipeline`, `GeometricNailSegmenter`, hand file store, vários ViewModels, androidTest fora do CI.

**Ações:** testes do núcleo try-on com Bitmap/fakes; gate `jacocoAppCoverageVerification`; cobrir erros do `ResultViewModel`.

### 4. Quality Reviewer — Aprovado com ressalvas

**Fortes:** Detekt/Lint/JaCoCo/Sonar integrados; PR #17 reduz smells.

**Ressalvas:** QG não bloqueia; exclusões otimistas; risco CPD nos dois recolorers; baseline Detekt possivelmente stale.

**Ações:** `SONAR_QUALITY_GATE_WAIT=true` pós-estabilização; unificar recolor; regenerar baseline.

### 5. Performance Reviewer — Risco alto (vision)

**Fortes:** try-on em `Dispatchers.Default`; Room com LIMIT.

**P0:** leaks em early-return; pico multi-bitmap; reprocessar MediaPipe a cada `polishColor`.

**Ações:** cache detect vs recolor; downsample correto; recycle em cancelamento/`finally`.

### 6. Security Reviewer — Aprovado com ressalvas

**OK:** cleartext off, FileProvider, sem secrets no git, Room parametrizado, Actions pinadas.

**P1:** `INTERNET`/`ACCESS_NETWORK_STATE` no merge (MediaPipe).

**P2:** path allowlist; limpar `hand_capture`; backup do path DataStore; `verify-metadata=true`.

### 7. Accessibility Reviewer — Não aprovado

**P0:** contraste secondary/branco-sobre-rosa; abas só por cor sem `selected`.

**P1:** alvos &lt;48dp; CD do try-on/loading; “VER TODAS ›” falso controle.

**Ações:** tokens `onSecondary`; FilterTab 48dp + semantics; anunciar status de prévia/salvamento.

### 8. UI Reviewer — Parcial

Alinha com a11y no contraste. Faltam `EmptyState`/`ErrorState`/`Loading` compartilhados; Histórico≈Favoritos; CTA morto no Result; `HandReferenceScreen` monolítico.

### 9. Documentation Reviewer — Aprovado com ressalvas

Atualizar README (try-on/mão), `AGENTS.md` (`assembleRelease` no `verifyCi`), smoke em `docs/release.md`, OFL das fontes, KDoc dos contratos de domínio.

### 10. Release Manager — Não pronta

Bloqueios: keystore release, AAB, changelog, listing/privacidade, smoke/Room 1→2 em device. Adequado só a QA interna com APK debug-signed.

### 11. CI/CD Reviewer — Ajustes P1

Ativar QG; reordenar quality→Sonar→assemble→upload; publicar APK/AAB + `jacocoAppReport`; restringir `permissions` e escopo do `SONAR_TOKEN`.

---

## Backlog sugerido (ordem)

| # | Item | Especialistas |
|---|------|---------------|
| 1 | Contraste + FilterTab a11y | Accessibility, UI |
| 2 | Recycle bitmaps + I/O IO dispatcher | Performance, Android |
| 3 | Quebrar ciclo data↔ui do try-on | Architecture, Android |
| 4 | Testes + gate cobertura app / try-on | Test, Quality |
| 5 | README + release.md + AGENTS | Documentation |
| 6 | Manifest rede / privacidade offline | Security, Release |
| 7 | CI: QG, artefatos, ordem | CI/CD, Quality |
| 8 | Keystore + AAB + changelog + listing | Release |

---

## Critérios para reavaliação

- A11y Scanner sem falhas de contraste/touch target nos fluxos Home → Result → Histórico → Minha mão.
- Sem leaks conhecidos de Bitmap nos early-returns; import de mão em `Dispatchers.IO`.
- Try-on sem dependência `data`→`ui`; testes do pipeline no CI.
- `SONAR_QUALITY_GATE_WAIT=true` verde; AAB assinado com keystore de upload.

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
| 8 | Keystore + AAB + listing Play | Em andamento — docs/listing/privacidade + workflow AAB; falta keystore da Márcia + Console |
