# Guia visual — Unhas de Que Cor?

Fonte de verdade: `design/guia/` (boards, logos e mockups claro/escuro).

Logos oficiais já estão no APK como `logo_mark` / `logo_horizontal` (tema claro/escuro) e no ícone do launcher.

- **Mark (só esmalte):** `drawable/logo_mark.webp` + `drawable-night/logo_mark.webp` — canto direito das páginas (`NailPolishMark` sem tint).
- **Lockup horizontal:** `logo_horizontal` — Home / Sobre.
- Fontes em `design/guia/logos/` (`logo-clara.png`, `logo-escura.png`).

Resultado: try-on sempre em foto real (amostra padrão se a usuária ainda não cadastrou a própria). Foto própria usa MediaPipe; amostras calibradas usam máscara/âncoras. Sem ilustração vetorial.

## Paleta

### Claro
| Token | Hex | Uso |
|-------|-----|-----|
| Base | `#FCF1EE` | Fundo |
| Superfície | `#ECB2C8` | Cards / chips |
| Diversão | `#F590B6` | FAB, tabs ativas, acentos |
| Ação | `#A4082B` | CTAs importantes |
| Identidade | `#400113` | Texto / marca |

### Escuro (board TEMA ESCURO)
| Token | Hex |
|-------|-----|
| Base | `#1A1120` |
| Cards | `#2C1F29` |
| Ação | `#D9468B` |
| Diversão | `#FFB6D5` |
| Texto | `#FCF1EE` |

## Tipografia
- Títulos: **Playfair Display**
- UI: **Poppins** (board claro; dark board citava Inter — seguimos Poppins da identidade)

## Composição das telas guia
1. **Home:** marca hero + 2 CTAs lado a lado + Inspiração do dia + explore (4) + últimas escolhas
2. **Contexto:** cards grandes de ocasião + moods + Continuar
3. **Resultado:** card hero visual + Salvar/Compartilhar + dica + cores parecidas
4. **Histórico:** tabs Todas/Favoritas + grupos por mês em cards + banner de stats
5. **Nav:** 5 destinos com FAB central “Escolher minha cor”
6. **Marca:** frasco em círculo quebrado + sparkles; “DE QUE COR?” em gradiente; tagline com divisor ✦

## Progresso do funil
Mockups mostram 1/5–3/5; produto MVP usa progresso real **1/2 → 2/2** (ocasião+mood → resultado).
