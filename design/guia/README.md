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
| `drawable/logo_horizontal.webp` | `logos/logo-horizontal-claro.png` | Home/Perfil (`BrandHeader`) |
| `drawable-night/logo_horizontal.webp` | `logos/logo-horizontal-escuro.png` | lockup escuro |
| `drawable/logo_mark.webp` | `logos/logo-icone.png` | referência / launcher |
| `drawable/logo_horizontal_mono.webp` | `logos/logo-mono.png` | mono / docs |
| `drawable/ic_launcher_{background,foreground}.webp` + mipmaps | `logo-icone.png` | ícone do app |

Nas toolbars (Contexto, Resultado, Histórico, Favoritos) o mark é o chip circular
vetorial (frasco + anel + sparkles) — o tile do ícone de app não é usado lá.

Telas em `telas/` são referência visual — não entram no APK.
