# Nail segmentation benchmark

## Objetivo

Impedir que a precisão do Try-On seja avaliada por uma única foto ou por ajustes manuais de constantes.

## Pipeline avaliado

```text
MediaPipe Hand Landmarks
        ↓
Nail ROI
        ↓
NailSegmenter
        ↓
NailMask + boundaryPolygon
        ↓
color application
```

MediaPipe localiza a mão/dedos. Ele não é a ground truth da lâmina ungueal.

## Dataset mínimo

Manter de 20 a 30 cenas anotadas, cobrindo:

- diferentes tons de pele;
- unhas curtas, médias e longas;
- esmalte claro, escuro e sem esmalte;
- polegar;
- dedos parcialmente sobrepostos;
- rotação/inclinação;
- iluminação boa, baixa e com highlights;
- fundos com objetos próximos;
- diferentes distâncias da câmera.

Cada cena deve ter uma máscara binária da lâmina ungueal. A anotação é feita uma vez e não é alterada pelo algoritmo.

## Gate de precisão

| Métrica | Gate |
|---|---:|
| IoU | ≥ 0,85 |
| erro médio de borda | ≤ 3 px |
| falso positivo | ≤ 2% |

A implementação em `NailSegmentationBenchmark` calcula IoU, precision, recall, erro de borda simétrico e falso positivo.

## Regra de decisão

Uma alteração só pode substituir o segmentador atual se:

1. passar o gate no conjunto inteiro;
2. não regredir nenhum cenário crítico;
3. melhorar ou manter o tempo de segmentação;
4. passar os testes automatizados.

Uma melhoria visual em uma única fotografia não é evidência suficiente.

## Performance

Para o modo Live, a meta posterior à precisão é:

- preview ≥ 15 FPS;
- segmentação pesada somente quando necessária;
- ROI pequena em vez da imagem inteira;
- tracking entre inferências;
- evitar alocações de `Bitmap` por frame.

A performance não deve ser otimizada antes de existir uma segmentação correta; depois disso ela passa a ser um gate de produção.
