# Nail Boundary Optimizer (NBO)

## 1. Objetivo

Definir matematicamente a fronteira visível da placa ungueal como uma curva contínua que combina evidência da imagem, probabilidade da segmentação, geometria do dedo e restrições anatômicas.

O NBO **não substitui** MediaPipe nem o modelo de segmentação. Ele transforma essas fontes de evidência em uma fronteira final única, contínua e fisicamente plausível.

A regra principal é conservadora:

> É preferível perder uma pequena parte da placa a pintar pele, cutícula, pregas, polpa ou ar.

---

## 2. Estado atual e motivação

O pipeline atual possui:

`MediaPipeHandNailDetector → ROI/eixo → segmentação → refinamento de fronteira → NailColorApplier`

MediaPipe fornece localização e orientação; não é tratado como contorno da unha. O modelo de segmentação fornece evidência de placa. O NBO deve ser uma etapa matemática explícita entre essa evidência e a máscara final.

O objetivo é substituir o acúmulo de heurísticas locais por um problema de otimização com função objetivo e restrições verificáveis.

---

## 3. Sistema de coordenadas local

Para cada unha, definir um sistema ortonormal local:

- `t`: coordenada longitudinal, da região proximal para a distal;
- `s`: coordenada transversal, da lateral esquerda para a direita;
- `C(t) = (x(t), y(t))`: curva de uma borda lateral;
- `t ∈ [0, 1]` após normalização da extensão longitudinal.

O eixo do dedo vindo do detector define a orientação inicial, mas não determina a fronteira final.

O polegar deve usar parâmetros próprios de proporção e curvatura; não assumir que sua geometria é a mesma dos demais dedos.

---

## 4. Representação da fronteira

A fronteira não deve ser modelada como uma sequência de pixels independentes.

Preferência: B-spline de grau `k`:

```text
C(t) = Σ Nᵢ,ₖ(t) Pᵢ
```

onde `Pᵢ` são pontos de controle e `Nᵢ,ₖ` são as funções de base da spline.

Isso permite uma fronteira contínua, com controle explícito de suavidade e curvatura e liberdade subpixel.

As duas laterais são independentes:

```text
C_L(t) ≠ mirror(C_R(t))
```

A simetria é uma hipótese fraca, nunca uma imposição global.

---

## 5. Problema de otimização

A solução procurada é:

```text
C* = argmin_C E(C)
```

com

```text
E(C) =
    λI E_image(C)
  + λM E_mask(C)
  + λG E_geometry(C)
  + λK E_curvature(C)
  + λA E_anatomy(C)
  + λS E_skin(C)
  + λT E_tip(C)
  + λC E_continuity(C)
```

Os pesos devem ser calibráveis por dados e não escolhidos para fazer uma captura específica parecer correta.

---

## 6. Evidência fotométrica

A imagem deve atrair a fronteira para transições reais entre placa e pele.

Definir um campo de custo `V(I, x, y)` derivado do gradiente local, por exemplo:

```text
V(x,y) = 1 / (1 + |∇I(x,y)|²)
```

Uma formulação contínua possível:

```text
E_image(C) = ∫ V(I, C(t)) |C'(t)| dt
```

O gradiente não é autoridade absoluta. Reflexo, sombra, textura, baixa iluminação e compressão podem produzir bordas falsas.

A amostragem da imagem deve ser subpixel quando a curva estiver entre pixels.

---

## 7. Evidência da segmentação

O modelo de segmentação deve ser tratado como probabilidade, quando disponível:

```text
p_N(x,y) = P(pixel pertence à placa | imagem)
```

Uma energia de compatibilidade pode ser:

```text
E_mask(C) = -∫ log(clamp(p_N(C(t)), ε, 1-ε)) dt
```

Para a região interna da placa, a solução deve favorecer alta probabilidade de unha. Próximo à fronteira, a evidência da imagem pode corrigir pequenos erros do modelo.

A segmentação nunca pode expandir a curva apenas porque isso aumenta cobertura.

---

## 8. Regularização por comprimento e curvatura

Para impedir ruído e micro-ondulações:

```text
E_length(C) = ∫ |C'(t)|² dt
```

```text
E_curvature(C) = ∫ |C''(t)|² dt
```

A segunda energia é particularmente importante para o defeito observado nas versões atuais: bordas com pequenas saliências e aparência de polígono rasterizado.

A regularização deve remover ruído sem apagar a curvatura anatômica real.

---

## 9. Restrição geométrica

O eixo e a ROI são priors.

Definir uma distância `d_axis` ao eixo plausível e penalizar desvios incompatíveis:

```text
E_geometry(C) = ∫ φ(d_axis(C(t))) dt
```

A penalidade deve ser suave dentro da região plausível e crescer rapidamente fora dela.

O prior geométrico não deve ser capaz de puxar a fronteira para um retângulo, losango ou elipse quando a imagem e a segmentação apresentam evidência anatômica melhor.

---

## 10. Anatomia

A anatomia entra como restrição, não como desenho fixo.

Restrições mínimas:

- proximal: não ultrapassar cutícula/eponíquio visível;
- laterais: não invadir pregas laterais;
- distal: não pintar ar ou polpa além da placa observada;
- comprimento: preservar o comprimento aparente da placa;
- forma: preservar squoval, quadrada, arredondada, amendoada etc. quando observáveis;
- polegar: usar modelo geométrico próprio;
- assimetria: permitir diferenças reais entre as laterais.

A função `E_anatomy` deve penalizar soluções incompatíveis com essas condições sem forçar uma anatomia idealizada sobre a fotografia.

---

## 11. Barreira contra pele

Este é um requisito de segurança visual de prioridade máxima.

Definir uma probabilidade ou mapa de risco periungueal `p_skin(x,y)`.

Uma barreira possível:

```text
E_skin(C) = ∫ B(p_skin(C(t))) dt
```

onde `B` cresce fortemente quando a curva entra em região classificada como pele não pertencente à placa.

A hierarquia de decisão é:

```text
pele / região proibida > evidência fraca de unha
```

Portanto, em caso de conflito, a curva deve recuar para dentro.

---

## 12. Free edge / ponta

A ponta é uma região onde o contraste pode ser enganoso.

A solução deve considerar simultaneamente:

- probabilidade da máscara;
- gradiente local;
- continuidade da curvatura;
- posição distal estimada;
- evidência de placa versus ar/polpa.

Não é permitido prolongar a unha apenas para obter uma aparência visualmente mais longa.

---

## 13. Condições de contorno

O problema deve explicitar condições proximais e distais.

Exemplos:

```text
C(0) ∈ Ω_proximal
C(1) ∈ Ω_distal
```

com condições de orientação:

```text
C'(0) ≈ T_proximal
C'(1) ≈ T_distal
```

As condições podem ser rígidas ou penalizadas, dependendo da confiança da detecção.

Isso transforma o refinamento em um problema de contorno, em vez de uma sequência arbitrária de deslocamentos de pixels.

---

## 14. Euler–Lagrange

Para uma energia funcional geral:

```text
J[C] = ∫ F(t, C, C', C'') dt
```

uma condição estacionária envolve a equação de Euler–Lagrange de ordem correspondente:

```text
∂F/∂C - d/dt(∂F/∂C') + d²/dt²(∂F/∂C'') = 0
```

Para o NBO, esta equação define o equilíbrio da curva quando a solução está no interior do domínio e as condições de contorno definem o comportamento nas extremidades.

A implementação não deve tentar resolver simbolicamente a equação. O objetivo é obter uma discretização estável e mensurável para Android.

---

## 15. Formulação discreta para o Android

A primeira implementação deverá usar `N` amostras longitudinais:

```text
C = {C₀, C₁, ..., Cₙ}
```

ou pontos de controle de uma spline.

Derivadas podem ser aproximadas por diferenças finitas centrais.

Exemplo:

```text
C'ᵢ   ≈ (Cᵢ₊₁ - Cᵢ₋₁) / (2h)
C''ᵢ  ≈ (Cᵢ₊₁ - 2Cᵢ + Cᵢ₋₁) / h²
```

A energia discreta deve ser calculável e decomponível por termo para diagnóstico.

O solver inicial deve privilegiar estabilidade e previsibilidade sobre complexidade.

---

## 16. Estratégia de solução

Ordem recomendada:

1. obter curva inicial a partir da máscara atual + eixo;
2. parametrizar como spline ou amostras regulares;
3. avaliar `E(C)` e cada componente;
4. otimizar sob restrições;
5. verificar convergência;
6. rejeitar soluções que violem barreiras anatômicas;
7. rasterizar a curva final para `NailMask`;
8. gerar `boundaryPolygon` a partir da mesma curva;
9. garantir que máscara e polígono sejam geometricamente consistentes.

O solver deve possuir um limite de iterações e um fallback determinístico para a máscara de entrada quando não houver evidência suficiente.

---

## 17. Critérios de convergência

Uma solução não deve ser considerada melhor apenas porque o contorno ficou visualmente mais suave.

Critérios possíveis:

```text
ΔE < ε_E
```

```text
max |ΔCᵢ| < ε_C
```

mais validações duras:

- nenhum spill proibido;
- nenhuma auto-interseção;
- nenhuma descontinuidade;
- nenhuma expansão distal sem evidência;
- área final dentro de faixa plausível em relação à máscara inicial.

---

## 18. Métricas de qualidade

O benchmark deve medir separadamente:

### Precisão de fronteira

Distância média e percentil 95 entre a curva estimada e ground truth.

### IoU

```text
IoU = |M_pred ∩ M_gt| / |M_pred ∪ M_gt|
```

### Boundary F-score

Avaliar especificamente pixels próximos à fronteira.

### Spill rate

Percentual de área pintada fora da placa real.

### Undercut rate

Percentual de placa real perdida pela solução.

### Shape error

Diferença de curvatura e comprimento em relação ao ground truth.

### Consistência temporal

Movimento da curva entre frames consecutivos sem mudança anatômica real.

---

## 19. Função de decisão do produto

A otimização matemática não pode maximizar somente IoU.

Para try-on, o custo visual do spill é maior que o custo de under-segmentation moderada:

```text
cost(spill) >> cost(undercut)
```

A ordem de prioridade do produto é:

1. não pintar pele;
2. respeitar a placa real;
3. preservar ponta e laterais;
4. preservar forma;
5. suavidade e estabilidade;
6. maximizar cobertura dentro das restrições.

---

## 20. Relação com a arquitetura existente

O NBO deve ser uma nova responsabilidade isolada.

```text
MediaPipeHandNailDetector
        ↓
NailRoiEstimator
        ↓
TfliteNailPlateSegmenter
        ↓
Nail Boundary Optimizer   ← nova camada matemática
        ↓
NailPlateMaskBoundaryGuard
        ↓
NailColorApplier
```

O `NailContourEdgeSpecialist` atual não deve receber mais heurísticas até que o NBO tenha sido validado.

A migração deve permitir comparação A/B:

```text
baseline = pipeline atual
candidate = pipeline + NBO
```

---

## 21. Princípios de implementação

- Não usar suavização como substituto de otimização.
- Não expandir a máscara para compensar erro do modelo.
- Não usar MediaPipe como contorno.
- Não forçar simetria entre laterais.
- Não transformar a ROI em formato de unha.
- Não esconder erro de segmentação no compositor.
- Não considerar uma curva bonita como evidência de correção.
- Toda alteração deve ser validada no benchmark antes do device test.
- O solver deve ser determinístico para a mesma entrada e parâmetros.
- Cada termo da energia deve poder ser ativado/desativado e medido isoladamente durante a fase de pesquisa.

---

## 22. Plano de implementação

### Etapa 1 — especificação

Este documento. Nenhuma alteração no pipeline de produção.

### Etapa 2 — protótipo JVM

Implementar o funcional e o solver em código testável fora do Android UI.

### Etapa 3 — benchmark

Executar baseline versus NBO em fixtures de mão/unhas com casos fáceis e difíceis.

### Etapa 4 — calibração

Ajustar pesos e restrições usando métricas, não screenshots individuais.

### Etapa 5 — integração Android

Integrar o solver no pipeline com limite de custo e fallback.

### Etapa 6 — performance

Medir tempo de otimização separadamente de MediaPipe e segmentação.

### Etapa 7 — device test

Somente após os gates JVM/benchmark passarem.

---

## 23. Critério de aprovação P0

O NBO só substitui o refinamento atual se demonstrar, no benchmark:

- menor spill;
- melhor erro de fronteira;
- melhor preservação de forma;
- nenhuma regressão crítica no polegar;
- estabilidade temporal aceitável;
- comportamento determinístico;
- custo computacional compatível com o modo live.

Se a solução não atingir esses critérios, ela não entra no pipeline principal.
