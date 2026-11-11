#!/usr/bin/env bash
# =====================================================================
# Instala Apache Druid en WSL (Ubuntu) como motor analitico / ML para el
# backup de facturas (tabla de hechos "hechos_ventas").
#
# No requiere sudo: todo queda en el HOME del usuario.
#   - Descarga un JDK 21 portatil (Eclipse Temurin) en ~/jdk-druid, que es el que exige Druid 38.
#   - Descarga la ultima version de Druid publicada en dlcdn.apache.org.
#
# Uso (desde Ubuntu en WSL):   bash install-druid.sh
# Luego:                       bash start-druid.sh
# =====================================================================
set -euo pipefail

INSTALL_DIR="${INSTALL_DIR:-$HOME}"
JDK_DIR="$INSTALL_DIR/jdk-druid"
DRUID_LINK="$INSTALL_DIR/druid"

java_major() {
  "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

# Druid 38 esta compilado para Java 21 (class file 65): Java 17 no sirve aunque verify-java lo mencione.
JAVA_REQUIRED=21

echo ">> 1/3 Java $JAVA_REQUIRED para Druid"
if [ -x "$JDK_DIR/bin/java" ] && [ "$(java_major "$JDK_DIR/bin/java")" -ge $JAVA_REQUIRED ]; then
  echo "   JDK portatil ya instalado en $JDK_DIR"
else
  echo "   Descargando Eclipse Temurin JDK $JAVA_REQUIRED (sin sudo)..."
  rm -rf "$JDK_DIR"
  mkdir -p "$JDK_DIR"
  curl -fL --progress-bar \
    "https://api.adoptium.net/v3/binary/latest/$JAVA_REQUIRED/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    | tar -xz -C "$JDK_DIR" --strip-components=1
  echo "   $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
fi

echo ">> 2/3 Apache Druid"
DRUID_VERSION="${DRUID_VERSION:-$(curl -s https://dlcdn.apache.org/druid/ | grep -oE '[0-9]+\.[0-9]+\.[0-9]+/' | sort -uV | tail -1 | tr -d /)}"
DRUID_DIR="$INSTALL_DIR/apache-druid-$DRUID_VERSION"
if [ -d "$DRUID_DIR" ]; then
  echo "   Druid $DRUID_VERSION ya esta en $DRUID_DIR"
else
  echo "   Descargando Druid $DRUID_VERSION (~650 MB)..."
  curl -fL --progress-bar "https://dlcdn.apache.org/druid/$DRUID_VERSION/apache-druid-$DRUID_VERSION-bin.tar.gz" \
    | tar -xz -C "$INSTALL_DIR"
fi
ln -sfn "$DRUID_DIR" "$DRUID_LINK"

# Perfil nano-quickstart (1 CPU / 4 GB): un solo slot de tareas de ingesta para ahorrar memoria.
# Las ingestas del backend usan maxNumConcurrentSubTasks=1, asi que un slot es suficiente.
sed -i 's/^druid.worker.capacity=.*/druid.worker.capacity=1/' \
  "$DRUID_DIR/conf/druid/single-server/nano-quickstart/middleManager/runtime.properties"

echo ">> 3/3 Verificacion"
echo "   Druid instalado en $DRUID_LINK -> $DRUID_DIR"
echo "   Siguiente paso: bash start-druid.sh   (consola web: http://localhost:8888)"
