# SonarCloud / SonarQube

Integração pronta no Gradle (`./gradlew sonar`) e no CI (só roda se `SONAR_TOKEN` existir).

## 1. Criar o projeto no SonarCloud

1. Acesse [sonarcloud.io](https://sonarcloud.io) e faça login com GitHub.
2. **Analyze new project** → escolha `marciacrisrs/unhas-de-que-cor-app` (ou org/time).
3. Anote:
   - **Organization** (ex.: `marciacrisrs`)
   - **Project key** (ex.: `marciacrisrs_unhas-de-que-cor-app`)
4. Gere um **token** (My Account → Security → Generate Tokens).

### Importante: desligar Automatic Analysis

Este repo analisa via **Gradle no GitHub Actions** (`./gradlew sonar`), com JaCoCo/Detekt/Lint.

Se o Automatic Analysis (GitHub App) ficar ligado ao mesmo tempo, o CI falha com:

```text
You are running CI analysis while Automatic Analysis is enabled.
```

No SonarCloud, no projeto:

1. **Administration → Analysis Method** (ou **General Settings → Analysis Method**)
2. Escolha **CI-based analysis** / **Disable Automatic Analysis**
3. Salve e re-rode o workflow da PR

> Self-hosted SonarQube: use a mesma config e defina `SONAR_HOST_URL` para a URL do servidor.

## 2. Secrets / variáveis no GitHub

Em **Settings → Secrets and variables → Actions**:

| Nome | Onde | Obrigatório | Exemplo |
|------|------|-------------|---------|
| `SONAR_TOKEN` | Secret | sim | token gerado no Sonar |
| `SONAR_ORGANIZATION` | Variable (ou Secret) | sim (Cloud) | `marciacrisrs` |
| `SONAR_PROJECT_KEY` | Variable (ou Secret) | sim | `marciacrisrs_unhas-de-que-cor-app` |
| `SONAR_HOST_URL` | Variable | não | default `https://sonarcloud.io` |
| `SONAR_QUALITY_GATE_WAIT` | Variable | não | `true` para falhar o CI se o Quality Gate falhar |

Enquanto `SONAR_TOKEN` estiver vazio, o job de CI **pula** o passo Sonar (verify continua normal).

## 3. Rodar localmente

Com token na shell (não commitar):

```bash
export SONAR_TOKEN=********
export SONAR_ORGANIZATION=sua-org
export SONAR_PROJECT_KEY=sua-org_unhas-de-que-cor-app

./gradlew sonar
```

Ou via propriedades Gradle:

```bash
./gradlew sonar \
  -Psonar.token=******** \
  -Psonar.organization=sua-org \
  -Psonar.projectKey=sua-org_unhas-de-que-cor-app
```

A tarefa `sonar` já depende de Detekt, Lint debug e JaCoCo app (`jacocoAppReport`).

## 4. O que é enviado

- Código Kotlin (`:app`)
- Cobertura JaCoCo (`jacocoAppReport.xml`) — domain, data testável, vision helpers e ViewModels
- Android Lint (`lint-results-debug.xml`)
- Detekt (relatório checkstyle/XML)
- Resultados JUnit unitários

Gates locais no `verifyCi`:
- `jacocoDomainCoverageVerification` — ≥80% linhas no pacote `domain`
- `jacocoAppCoverageVerification` — ≥80% linhas no escopo do relatório Sonar

## 5. Quality Gate

Por padrão `SONAR_QUALITY_GATE_WAIT=false` — a análise sobe sem quebrar o CI.  
Quando a dashboard estiver estável, defina a variable `SONAR_QUALITY_GATE_WAIT=true` no GitHub.

## 6. PR decoration (opcional)

No SonarCloud: Administration → General Settings → Pull Requests / DevOps Platform → bind ao GitHub App da org.  
Com isso, issues aparecem no próprio PR.

## 7. Supply chain (Sonar security)

Para o Quality Gate de segurança:

- Actions do CI pinadas por **SHA completo** (`.github/workflows/ci.yml`)
- `gradle/verification-metadata.xml` (checksums sha256 das dependências)

Ao adicionar/atualizar dependências:

```bash
./gradlew --write-verification-metadata sha256 verifyCi --no-configuration-cache
```

Revise o diff do XML e faça commit.
