# Teste em dispositivo Android real (ISSUE 004 / #53)

Este ambiente de CI/cloud **não** sobe emulador (nested virt). Validação em aparelho
é **manual**, feita pela mantenedora antes do release.

Checklist operacional de release (curto): [`docs/release.md`](release.md) § Smoke.
Matriz JVM: [`docs/vision-test-matrix.md`](vision-test-matrix.md).

## Preparação

- [ ] 2 devices (tamanhos diferentes quando possível)
- [ ] Android próximo do `minSdk` e um LTS recente
- [ ] APK/AAB da build a validar (`assembleRelease` ou artifact do workflow)
- [ ] Limpar dados do app entre rodadas críticas

## Relatório (copie por device)

| # | Caso | Pass? | Notas / tempo |
|---|------|-------|---------------|
| 1 | Câmera + permissão concedida | | |
| 2 | Galeria + permissão | | |
| 3 | Permissão câmera negada → mensagem clara | | |
| 4 | Try-on mão esquerda / direita | | |
| 5 | Luz natural clara | | |
| 6 | Indoor | | |
| 7 | Baixa luz | | |
| 8 | Contraluz | | |
| 9 | Flash direto | | |
| 10 | Sem flash | | |
| 11 | Reflexo forte na unha | | |
| 12 | Mão longe → feedback “aproxime…” | | |
| 13 | Mão de lado / punho → ângulo / aproximada | | |
| 14 | Sem mão na foto → não detectada | | |
| 15 | Retrato / paisagem / quadrado | | |
| 16 | Processamento &lt; ~3s, UI responsiva | | |
| 17 | Volta câmera/galeria sem crash | | |
| 18 | TalkBack: banner de falha anunciado | | |
| 19 | Accessibility Scanner sem erro bloqueante | | |
| 20 | **Pele retinta** · luz natural frontal | | |
| 21 | **Pele retinta** · indoor lâmpada na frente | | |
| 22 | **Pele retinta** · esmalte escuro (vinho) | | |
| 23 | Checklist Minha mão visível + CTA captura | | |

## Bugs

Abrir issue `bug` / `regression` com: device, Android, foto (se possível), claim
exibido (FULL / APPROXIMATE / NOT_DETECTED) e mensagem tipada.

## Fora deste doc

Play Console, keystore e listing: [`docs/release.md`](release.md).
