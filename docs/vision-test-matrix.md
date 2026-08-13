# Matriz de testes de visão (ISSUE 003 / #52)

Suíte de regressão do pipeline `detect → classify → recolor → render`.
Fixtures fotográficas grandes **não** entram no git; a cobertura crítica roda em **JVM**
com landmarks/scores sintéticos + amostras de produto já versionadas.

## Onde rodam

| Camada | Comando / local |
|--------|-----------------|
| Unitários JVM | `./gradlew :app:testDebugUnitTest` (parte de `verifyCi`) |
| Amostras de produto | `app/src/main/assets/hand_samples/` (try-on de exemplo, não regressão MediaPipe) |
| Device real | [`docs/device-testing.md`](device-testing.md) — OUT_OF_REPO neste VM |

## Matriz → testes automatizados

### Detecção de mão

| Caso | Cobertura JVM | Arquivo / nota |
|------|---------------|----------------|
| Frontal bem iluminada | Presence STRONG + tip-span aberto → FULL | `TryOnHandReliabilityTest`, `NailTryOnPipelineTest` |
| Inclinada / mid presence | WEAK → nunca FULL | `TryOnDifficultConditionsTest` |
| Muito próxima / grande | Extent alto; sem reject por tamanho | `DetectionFailureDiagnosticsTest` (inverso de HandTooFar) |
| Muito distante / pequena | `HandTooFar` | `DetectionFailureDiagnosticsTest` |
| Esquerda / direita | `Handedness` no scoring | `HandPresenceScoringTest` |
| Espelhada | Variante mirror no enhancer | `HandInferenceEnhancerTest` / variantes (MediaPipe excluído do JaCoCo) |
| Pele retinta / subexposta | `deepSkinLift` + TooDark só &lt;40 | `HandTrainingScenesTest`, `DetectionFailureDiagnosticsTest` |

### Detecção de unha

| Caso | Cobertura JVM | Arquivo / nota |
|------|---------------|----------------|
| Uma unha | Demote STRONG→WEAK se &lt;2 paintable | `NailTryOnPipelineTest` |
| Múltiplas (2–5) | FULL com ≥3 × `NAIL_FULL_MIN` | `TryOnHandReliabilityTest` |
| Parcial / oclusão | Tip-span baixo → WEAK / BadAngle | `TryOnDifficultConditionsTest` |
| Placa pequena / grande | `NailPlateCalibration` + mapper | `NailPlateCalibrationTest`, `NailLandmarkMapperTest` |
| Esmalte escuro em pele retinta | `darkerPolish` no segmenter | `GeometricNailSegmenterTest` |

### Iluminação

| Caso | Cobertura JVM | Arquivo / nota |
|------|---------------|----------------|
| Diurna / clara | Caminho feliz (presence alta) | reliability / pipeline |
| Baixa / indoor | `TooDark` via mean luma | `DetectionFailureDiagnosticsTest` |
| Contraluz | Enhance stretch/gamma | `HandInferenceEnhancerTest` |
| Flash direto | Highlight share + tip glare | `TryOnDifficultConditionsTest`, diagnostics |
| Reflexo na unha | Tip glare cap &lt; STRONG | `HandPresenceScoringTest` |
| Sombra | Lift brightness variantes | enhancer |

### Negativos

| Caso | Cobertura JVM | Arquivo / nota |
|------|---------------|----------------|
| Sem mão | `detect` → null (sem landmarks) | `NailTryOnPipelineTest` |
| Presence &lt; floor | Snapshot `REJECTED` + motivo | pipeline + difficult conditions |
| Sem unha paintable | `NoNailVisible` | `DetectionFailureDiagnosticsTest` |

### Extremos

| Caso | Cobertura JVM | Device (#53) |
|------|---------------|--------------|
| Rotação 0/90/180/270 | Orientation fallback (MediaPipe path) | checklist device |
| Múltiplas mãos | Heurística `ClutteredScene` (mensagem pronta) | validar em device |
| Mão parcial fora do frame | Mapper / tip-span | device + mapper tests |

## Floor de confiança (#51)

Valores canônicos: `DetectionConfidenceFloor`.
Testes: `DetectionConfidenceFloorTest`, pipeline early-reject, `filterPaintable`.

## Feedback tipado (#54)

| Motivo | Mensagem (resumo) | Detector |
|--------|-------------------|----------|
| `HandTooFar` | Aproxime a mão… | extent &lt; 0.12 |
| `TooDark` | Foto mais iluminada… | mean luma &lt; 55 |
| `ExcessiveGlare` | Evite reflexos… | highlight share / tip glare |
| `BadAngle` | Palma e unhas de frente… | tip-span baixo |
| `NoNailVisible` | Não identifiquei unha… | zero paintable |
| `ClutteredScene` | Só a foto da mão… | reservado / device |
| `Generic` | Tente outra foto… | fallback |

UI: `TryOnPreviewLabels` + banner / TalkBack + CTA “Tentar outra foto”.

## Fixtures futuras (opcional)

Se adicionar fotos reais: `app/src/test/resources/vision-fixtures/` + tabela
`fixture → expected reliability → expected reason`. Manter arquivos pequenos (&lt;200 KB)
e documentar o esperado neste arquivo.
