# Mãos de exemplo e treino (try-on)

## Catálogo (`HandSampleCatalog`)

Ordem no picker: **pele retinta primeiro** (treino / prioridade mãe), depois morena, clara, etc.

| ID | Tom | Uso |
|----|-----|-----|
| `retinta_vinho` | Retinta | Treino prioridade |
| `retinta_polegar` | Retinta | Treino · pose diversa |
| `morena_nude` | Morena | Referência |
| `clara_vermelho` | Clara | **Única máscara calibrada** (`MASK_SAMPLES`) + default do app |
| `morena_clara_coral` | Morena clara | Unhas curtas |
| `media_rosa` | Média | Unhas longas |

Fotos: `app/src/main/assets/hand_samples/`.  
Máscaras: `app/src/main/assets/hand_nail_masks/`.

## Máscara calibrada

Só IDs em `NailOverlayAnchors.MASK_SAMPLES` usam PNG de máscara.  
Hoje: `clara_vermelho`. Demais amostras usam **MediaPipe** na foto (sem elipse que pinta pele).

Para reativar uma máscara:

1. Remask pixel-accurate (placa só, soft edge, cobertura ≤ 18%).
2. Regenerar âncoras pelos centróides.
3. Incluir o id em `MASK_SAMPLES` + `HandSampleMaskAssetTest`.
4. Revisar com `vision-tryon-reviewer`.

## Treino JVM (pele retinta)

`HandTrainingScenes` — cenas sintéticas (RGB) para segmentação/luminância sem fotos grandes no git.  
Prioridade: `retinta_natural_plate`, `retinta_wine_polish`, `retinta_underexposed`.

## Captura da foto da usuária

Checklist em `HandCaptureGuidance` (tela Minha mão + confirmação):

- Luz frontal (janela/lâmpada); evitar contraluz e flash direto  
- Unhas de frente, dedos abertos  
- Mão preenchendo o quadro  
- Em pele retinta: luz natural ou lâmpada **na frente** da mão  

Pipeline: variantes `deepSkinLift` cedo quando a luminância média está em faixa de pele profunda subexposta (`HandInferenceVariants`).
