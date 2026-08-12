# Avaliação dos especialistas — Unhas de Que Cor?

**Data:** 2026-08-10 (follow-ups até 2026-08-12)  
**Base inicial:** `master` @ `5a8663b`  
**Fonte de verdade:** `.github/agents/*` + `.github/copilot-instructions.md`

## Painel (pós follow-ups + UX #35 + vision #36)

| Especialista | Veredito atualizado (12e) |
|--------------|---------------------|
| Android Engineer | Aprovado c/ ressalvas (#35/#36) |
| Architecture Reviewer | Aprovado (#36) / ressalvas (#35 Result→Home) |
| Test Engineer | Aprovado c/ ressalvas (flash coberto; paint paths abertos) |
| Quality Reviewer | Aprovado c/ ressalvas — merge após QG verde |
| Performance Reviewer | Aprovado |
| Security Reviewer | Aprovado |
| Accessibility Reviewer | Aprovado c/ ressalvas (banner try-on; maxLines convite) |
| UI Reviewer | Aprovado (#36) / ressalvas (#35 chrome Favoritos) |
| Documentation Reviewer | Aprovado c/ ressalvas (este painel 12e) |
| Release Manager | Aprovado c/ ressalvas — smoke try-on em device antes de store |
| CI/CD Reviewer | Aprovado c/ ressalvas — Verify #35/#36 em andamento |

**Síntese:** pedidos de UX (#35) e correção do overlay DEFAULT (#36) aprováveis
após CI/QG verdes. Reavaliação completa em **2026-08-12e**. Resta operação de loja
(keystore + listing) e validação visual em device (`OUT_OF_REPO`).

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

---

## Follow-up Architecture DIP (2026-08-12b)

Branch `cursor/especialistas-completo-535f` — fecha as últimas violações de Dependency
Inversion apontadas pelo Architecture Reviewer (ViewModels resolvendo dependências de
`data`/`android.*` diretamente em vez de receber via construtor/domínio):

| Item | Status |
|------|--------|
| `HandTryOnPreview` resolvia `NailTryOnPipeline` via `EntryPointAccessors` (Hilt EntryPoint) dentro do Composable | Feito — `NailPipelineEntryPoint` removido; `ResultViewModel` injeta `NailTryOnPipeline` via `@Inject constructor` e repassa como parâmetro (`ResultScreen` → `HandTryOnPreview(nailPipeline = ...)`) |
| `HandReferenceViewModel` dependia de `@ApplicationContext Context` + `HandReferenceFileStore` (I/O de arquivo/`ContentResolver` direto na camada de apresentação) | Feito — `HandReferenceRepository` ganhou `stageFromContentUri`/`stageSampleAsset`/`createCameraCapturePath`/`clearStagingCache`/`clearStagingCacheNow` (paths como `String`, sem `Uri`/`Context` no domínio); `HandReferenceViewModel` agora só depende de use cases + `HandReferenceRepository` |
| `save()` duplicava limpeza do cache de staging em cada call site do ViewModel | Feito — `HandReferenceRepositoryImpl.save()` limpa o cache de staging uma única vez, tanto em `Saved` quanto em `Rejected` |
| Cobertura dos casos de erro do `ResultViewModel` (`generateAndSave` falhando, `restoreRecommendation` retornando null) | Feito — novos testes em `ResultViewModelTest` |
| Cobertura de `HandReferenceRepositoryImpl` (arquivo órfão no `observe()`, limpeza de staging no `save()`) | Feito — `HandReferenceRepositoryImplTest` novo em `data/repository/` |
| Licença das fontes bundladas (Playfair/Poppins) | Feito — `app/src/main/assets/font/OFL.txt` (SIL OFL 1.1); `res/font/` só aceita `.ttf/.otf/.ttc/.xml`, por isso o pointer de licença vive em `assets/` |
| KDoc em interfaces de repositório sem documentação | Feito — `HandReferenceRepository`, `HistoryRepository`, `ColorCatalogRepository` |
| Tela "Sobre" (About) | Feito — `ui/about/AboutScreen.kt` + rota; entrada no Perfil |
| Máscaras de amostra (3 faltantes) | Feito — PNG em `hand_nail_masks/` para todas as 5 amostras |

**Fora do repositório (`OUT_OF_REPO`, operação de loja/infra — não resolvível em código):**

- Keystore de upload assinado nos secrets do CI (`RELEASE_*`/`ANDROID_*`), para gerar AAB de release assinado fora do debug-signing fallback.
- Publicação/atualização do listing na Google Play Console (screenshots, descrição, política de privacidade hospedada) — conteúdo já preparado em `docs/play-listing.md`/`docs/privacy-policy.md`.

---

## Follow-up UX+vision (2026-08-12d) — reavaliação conjunta

Reavaliação dos PRs abertos vs `origin/master`, contra `.github/agents/*` +
`.github/copilot-instructions.md`:

- **#35** `cursor/ux-home-favoritos-sobre-535f` — Home / Favoritos / Sobre / logo / Minha mão  
- **#36** `cursor/fix-nail-overlay-offset-535f` — esmalte longe das unhas (DEFAULT + bias)

Sem overlap de arquivos entre os dois PRs.

### Painel

| Especialista | Veredito | Notas |
|--------------|----------|-------|
| Android Engineer | Ressalvas | Overlay DEFAULT removido; `FACING_CENTER` unificado 0.82 |
| Architecture Reviewer | Ressalvas | Flash `SavedStateHandle` correto; Result→mão → Home |
| Test Engineer | Ressalvas | Flash Home coberto; paint paths try-on ainda privados |
| Quality Reviewer | Ressalvas (#35 QG coverage) → alvo: testes HomeViewModel |
| Performance Reviewer | OK | Menos Canvas sem detecção |
| Security Reviewer | OK | Sobre → GitHub HTTPS |
| Accessibility Reviewer | Ressalvas | Banner try-on: CD+texto; contraste semi-transparente |
| UI Reviewer | Ressalvas | Pedidos UX atendidos; chrome Favoritos ≠ Histórico |
| Documentation Reviewer | OK | Este painel |
| Release Manager | Ressalvas | Sem bump; `OUT_OF_REPO` intacto |
| CI/CD Reviewer | Ressalvas | #35 bloqueado por QG até coverage; pipeline ok |

**Veredito global:** #36 aprovável (correção de alinhamento válida). #35 aprovável
após `new_coverage` ≥ 80% (lacuna: ~6 linhas do `HomeViewModel` flash). Sem Bloqueio
de produto no bug das ovais flutuantes.

### PR #35 — UX

| Pedido | Status |
|--------|--------|
| Home compacta / scroll só se necessário | Feito |
| Favoritos com Voltar | Feito |
| Sobre: GitHub no lugar do e-mail | Feito + nota privacidade Play |
| `NailPolishMark` ≈ logo oficial | Feito |
| Minha mão → Home + feedback | Feito (`navigateHome` + flash) |

### PR #36 — vision

| Mudança | Avaliação |
|---------|-----------|
| Sem landmarks → `anchors = emptyList()` (não DEFAULT) | Correto |
| Status approximate-with-anchors vs não-detectada | Melhora textual |
| Centro 0.58 / overshoot 0.02 / bias elipse +0.04 | Coerente |
| `FACING_CENTER` mapper↔ROI | Unificado em 0.82 |

### Backlog residual (pós 12d)

| Pri | Item | Status |
|-----|------|--------|
| P0 | Testes `HomeViewModel` flash p/ QG #35 | Feito (`HomeViewModelTest` / commit `0ab112f`) |
| P1 | Testes ramos `paintUserPreview` (extrair se preciso) | Aberto |
| P1 | A11y banner try-on (semantics/contraste) | Aberto |
| P2 | Smells HistoryScreen / HandReferenceEffects | Aberto |
| P2 | `DetectedNailPolishApplier` unit tests | Aberto |
| P2 | Result→mão: destino pós-save (Home vs Result) | Aberto |
| P2 | Chrome Favoritos vs Histórico | Aceito / residual |
| — | Keystore + listing Play | **OUT_OF_REPO** |
| — | A11y Scanner em device | **OUT_OF_REPO** |

---

## Reavaliação 2026-08-12e — especialistas novamente

Pedido: nova passagem completa contra `.github/agents/*` nos PRs abertos vs
`origin/master` @ `1b42c87` (pós #33/#34 release internal Play).

| PR | Branch | Escopo |
|----|--------|--------|
| [#35](https://github.com/marciacrisrs/unhas-de-que-cor-app/pull/35) | `cursor/ux-home-favoritos-sobre-535f` @ `0ab112f` | Home / Favoritos / Sobre / logo / Minha mão + flash |
| [#36](https://github.com/marciacrisrs/unhas-de-que-cor-app/pull/36) | `cursor/fix-nail-overlay-offset-535f` @ `7e9201b`+ | Sem DEFAULT errado; geometria mapper/ROI |

CI no momento da avaliação: ambos os jobs `Verify` **IN_PROGRESS** (Sonar ainda
não confirmado nesta passagem). Emulador indisponível nesta VM.

### Veredito global

| PR | Merge | Motivo |
|----|-------|--------|
| #35 | **Aprovar após QG verde** | Cinco pedidos UX atendidos; `HomeViewModelTest` cobre flash; P0 restante = confirmação Sonar |
| #36 | **Aprovar com ressalvas** | Bug das ovais flutuantes corrigido; calibração fina exige smoke em device |

Sem bloqueio de produto novo. Sem overlap de arquivos entre #35 e #36.

### Painel por especialista (consolidado)

| Especialista | #35 | #36 | Achados principais |
|--------------|-----|-----|-------------------|
| Android Engineer | Ressalvas | Ressalvas | Flash/`SavedStateHandle` ok; `getBackStackEntry(HOME)` sem guarda (**P1**); constantes mapper↔ROI ainda duplicadas (**P2**) |
| Architecture Reviewer | Ressalvas | Aprovado | Result→mão → Home perde contexto (**P2**); UI não viola camadas no try-on |
| Test Engineer | Ressalvas | Ressalvas | Flash coberto; falta teste navegação handle (**P1**); falta ramo `emptyList()`/`paintUserPreview` e applier (**P1/P2**) |
| Quality Reviewer | Ressalvas | Ressalvas | Merge só com Verify+QG verdes (**P0** condicional); `tipPipPx` morto no mapper (**P3**) |
| Performance Reviewer | Aprovado | Aprovado | Menos Canvas sem detecção; Home compacta sem I/O na Main |
| Security Reviewer | Aprovado | Aprovado | Sobre → GitHub HTTPS; vision sem superfície nova |
| Accessibility Reviewer | Ressalvas | Ressalvas | Convite mão `maxLines=1` (**P1**); banner try-on contraste/CD (**P1** residual) |
| UI Reviewer | Ressalvas | Aprovado | Pedidos UX feitos; chrome Favoritos ≠ Histórico (**P2**); estado “mão não detectada” honesto |
| Documentation Reviewer | Ressalvas | Aprovado | Status P0 testes corrigido neste 12e; CHANGELOG do fix vision no próximo release |
| Release Manager | Ressalvas | Ressalvas | Sem bump (ok); store só após smoke try-on + `OUT_OF_REPO` |
| CI/CD Reviewer | Ressalvas | Ressalvas | Nenhum workflow alterado; aguardar Verify #35/#36 |

### PR #35 — detalhe

| Pedido | Status 12e |
|--------|------------|
| Home compacta / scroll só se necessário | Feito |
| Favoritos com Voltar | Feito |
| Sobre: GitHub (não e-mail) | Feito + nota privacidade Play |
| `NailPolishMark` ≈ logo | Feito |
| Minha mão → Home + feedback | Feito (`navigateHome` + flash + teste) |

**Riscos residuais #35:** QG Sonar; crash se HOME ausente da back stack; Result
perdido após cadastro de mão; a11y `maxLines=1` no convite.

### PR #36 — detalhe

| Mudança | Status 12e |
|---------|------------|
| Sem landmarks → `anchors = emptyList()` | Correto — não pinta DEFAULT |
| Status approximate vs não-detectada | Melhora textual / CD |
| Centro 0.58 / overshoot 0.02 / bias +0.04 | Coerente; device TBD |
| Polegar proximal + `FACING_CENTER` 0.82 | Unificado mapper↔ROI |

**Riscos residuais #36:** alinhamento em ângulos reais; regressão DEFAULT sem teste
de paint path; constantes mágicas sem ground-truth.

### Backlog residual (pós 12e)

| Pri | Item | Status |
|-----|------|--------|
| P0 | Confirmar QG Sonar #35/#36 na CI | Em andamento (Verify) |
| P1 | Testes `paintUserPreview` / empty anchors | Aberto |
| P1 | Guarda `getBackStackEntry(HOME)` | Aberto |
| P1 | A11y: banner try-on + convite `maxLines` | Aberto |
| P2 | Result→mão destino; chrome Favoritos; smells History | Aberto |
| P2 | Unit tests `DetectedNailPolishApplier`; DRY constantes vision | Aberto |
| — | Keystore + listing Play | **OUT_OF_REPO** |
| — | A11y Scanner + smoke try-on em device | **OUT_OF_REPO** |
