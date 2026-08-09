# Imagens guia — Unhas de Que Cor?

Fonte de verdade do design (boards, logos e mockups de tela).

## Estrutura

```
design/guia/
  logos/
    logo-horizontal-claro.png
    logo-horizontal-escuro.png
    logo-icone.png
    logo-mono.png
  boards/
    brand-board-escuro.png
  telas/
    claro/
      brand-board-claro.png
      home.png
      contexto.png
      resultado.png
      historico.png
      cores-parecidas.png
    escuro/
      home.png
      contexto.png
      resultado.png
      historico.png
      cores-parecidas.png
```

## Uso no app (runtime)

Assets otimizados derivados dos logos oficiais:

| Recurso | Origem | Uso |
|---------|--------|-----|
| `drawable/logo_mark.webp` | `logos/logo-icone.png` | `NailPolishMark` / header |
| `drawable/logo_horizontal.webp` | `logos/logo-horizontal-claro.png` | lockup (claro) |
| `drawable-night/logo_horizontal.webp` | `logos/logo-horizontal-escuro.png` | lockup (escuro) |
| `drawable/logo_horizontal_mono.webp` | `logos/logo-mono.png` | mono / docs |
| `drawable/ic_launcher_{background,foreground}.webp` + mipmaps | `logo-icone.png` | ícone do app |

Telas em `telas/` são referência visual — não entram no APK.
