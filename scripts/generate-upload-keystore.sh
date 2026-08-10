#!/usr/bin/env bash
# Gera um upload keystore para Play App Signing (NÃO commitado).
# Uso:
#   ./scripts/generate-upload-keystore.sh
#   ./scripts/generate-upload-keystore.sh ~/keys/unhas-upload.jks upload
set -euo pipefail

OUT="${1:-$HOME/keys/unhas-de-que-cor-upload.jks}"
ALIAS="${2:-upload}"
VALIDITY_DAYS="${VALIDITY_DAYS:-10000}"

mkdir -p "$(dirname "$OUT")"
if [[ -e "$OUT" ]]; then
  echo "Arquivo já existe: $OUT" >&2
  echo "Apague ou passe outro caminho." >&2
  exit 1
fi

echo "Gerando keystore em: $OUT"
echo "Alias: $ALIAS"
keytool -genkeypair \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -storetype JKS

cat <<EOF

Pronto. Configure (não commitar senhas):

  # ~/.gradle/gradle.properties  — ou export na shell
  RELEASE_STORE_FILE=$OUT
  RELEASE_STORE_PASSWORD=<a senha que você digitou>
  RELEASE_KEY_ALIAS=$ALIAS
  RELEASE_KEY_PASSWORD=<mesma senha ou a do key>

Depois:

  ./gradlew :app:bundleRelease

AAB: app/build/outputs/bundle/release/app-release.aab

Guarde backup offline do .jks + senhas. Com Play App Signing, este é o
*upload key*; a key de assinatura da loja fica com o Google.
EOF
