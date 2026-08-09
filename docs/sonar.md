# SonarCloud / SonarQube

Integração pronta no Gradle (`./gradlew sonar`) e no CI (só roda se `SONAR_TOKEN` existir).

## 1. Criar o projeto no SonarCloud

1. Acesse [sonarcloud.io](https://sonarcloud.io) e faça login com GitHub.
2. **Analyze new project** → escolha `marciacrisrs/unhas-de-que-cor-app` (ou org/time).
3. Anote:
   - **Organization** (ex.: `marciacrisrs`)
   - **Project key** (ex.: `marciacrisrs_unhas-de-que-cor-app`)
4. Gere um **token** (My Account → Security → Generate Tokens).

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

A tarefa `sonar` já depende de Detekt, Lint debug e JaCoCo domain.

## 4. O que é enviado

- Código Kotlin (`:app`)
- Cobertura JaCoCo do pacote `domain` (`jacocoDomainReport.xml`)
- Android Lint (`lint-results-debug.xml`)
- Detekt (relatório checkstyle/XML)
- Resultados JUnit unitários

## 5. Quality Gate

Por padrão `SONAR_QUALITY_GATE_WAIT=false` — a análise sobe sem quebrar o CI.  
Quando a dashboard estiver estável, defina a variable `SONAR_QUALITY_GATE_WAIT=true` no GitHub.

## 6. PR decoration (opcional)

No SonarCloud: Administration → General Settings → Pull Requests / DevOps Platform → bind ao GitHub App da org.  
Com isso, issues aparecem no próprio PR.
