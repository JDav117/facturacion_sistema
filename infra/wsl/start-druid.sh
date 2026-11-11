#!/usr/bin/env bash
# =====================================================================
# Arranca Apache Druid en WSL2 con el perfil "micro-facturacion":
# una version reducida del perfil oficial nano-quickstart para equipos de 16 GB.
#   ZooKeeper + coordinator/overlord + broker + historical + middleManager + router
#   Heaps a la mitad, GC serial, sin compilador C2 y buferes pequenos (~1,5 GB en total).
#
# Consola web y API (router): http://localhost:8888
# Uso: bash start-druid.sh   y DEJAR LA TERMINAL ABIERTA (Ctrl+C o stop-druid.sh para detener)
# =====================================================================
set -uo pipefail

DRUID_HOME="${DRUID_HOME:-$HOME/druid}"
PROFILE="micro-facturacion"

if [ -x "$HOME/jdk-druid/bin/java" ]; then
  export JAVA_HOME="$HOME/jdk-druid"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

health() { curl -s http://127.0.0.1:8888/status/health 2>/dev/null; }

if [ "$(health)" = "true" ]; then
  echo "Druid ya esta corriendo en http://localhost:8888"
  exit 0
fi

if [ ! -d "$DRUID_HOME/conf/druid/single-server/nano-quickstart" ]; then
  echo "No se encontro Druid en $DRUID_HOME. Ejecute primero: bash install-druid.sh"
  exit 1
fi

cd "$DRUID_HOME"

# ---------------------------------------------------------------- perfil micro
NANO="conf/druid/single-server/nano-quickstart"
MICRO="conf/druid/single-server/$PROFILE"
# preferIPv4Stack: sin esto Druid escucha en IPv6 y WSL solo lo publica en [::1] de Windows,
# de modo que Java/Spring (que usa 127.0.0.1) recibe "Connection refused".
LOW_MEM_FLAGS="-XX:+UseSerialGC\n-XX:TieredStopAtLevel=1\n-XX:ReservedCodeCacheSize=48m\n-Xss512k\n-Djava.net.preferIPv4Stack=true"

rm -rf "$MICRO"
cp -r "$NANO" "$MICRO"

# set_jvm <servicio> <heap> [directa]
set_jvm() {
  local file="$MICRO/$1/jvm.config"
  sed -i -E "s/^-Xms[0-9]+[mg]$/-Xms$2/; s/^-Xmx[0-9]+[mg]$/-Xmx$2/" "$file"
  if [ -n "${3:-}" ]; then
    sed -i -E "s/^-XX:MaxDirectMemorySize=.*/-XX:MaxDirectMemorySize=$3/" "$file"
  fi
  sed -i "s/^-server$/-server\n$LOW_MEM_FLAGS/" "$file"
}

# set_prop <servicio> <clave> <valor>
set_prop() {
  local file="$MICRO/$1/runtime.properties"
  if grep -q "^$2=" "$file"; then
    sed -i "s|^$2=.*|$2=$3|" "$file"
  else
    echo "$2=$3" >> "$file"
  fi
}

set_jvm coordinator-overlord 128m
set_jvm broker 256m 160m
set_jvm historical 256m 160m
set_jvm router 64m 64m
set_jvm middleManager 64m

for svc in broker historical; do
  set_prop $svc druid.processing.buffer.sizeBytes 25MiB
  set_prop $svc druid.processing.numMergeBuffers 1
  set_prop $svc druid.processing.numThreads 1
done
set_prop historical druid.cache.sizeInBytes 8MiB

# Tareas de ingesta (peons): un solo slot y JVM pequena
set_prop middleManager druid.worker.capacity 1
set_prop middleManager druid.indexer.fork.property.druid.processing.numMergeBuffers 1
sed -i -E 's/"-Xms[0-9]+m","-Xmx[0-9]+m","-XX:MaxDirectMemorySize=[0-9]+m"/"-Xms192m","-Xmx192m","-XX:MaxDirectMemorySize=120m","-XX:+UseSerialGC","-XX:TieredStopAtLevel=1","-Djava.net.preferIPv4Stack=true"/' \
  "$MICRO/middleManager/runtime.properties"

# Supervisor: mismos servicios que nano, apuntando al perfil micro
sed "s|nano-quickstart|$PROFILE|g" conf/supervise/single-server/nano-quickstart.conf \
  > "conf/supervise/single-server/$PROFILE.conf"

# ---------------------------------------------------------------- arranque
# Druid corre en PRIMER PLANO: WSL apaga Ubuntu cuando no queda ninguna terminal abierta,
# asi que esta terminal debe quedar abierta mientras se usa el sistema (Ctrl+C lo detiene).
mkdir -p "$DRUID_HOME/log"
rm -f log/*.log   # logs limpios en cada arranque (Druid los acumula)

(
  for i in $(seq 1 90); do
    sleep 2
    if [ "$(health)" = "true" ]; then
      echo ""
      echo "=============================================================="
      echo " Druid arriba: consola en http://localhost:8888 (~$((i * 2)) s)"
      echo " Deje esta terminal abierta. Ctrl+C para detener Druid."
      echo "=============================================================="
      exit 0
    fi
  done
  echo "Druid no respondio en 180 s. Revise $DRUID_HOME/log/*.log"
) &

echo "Iniciando Druid (perfil $PROFILE)..."
exec bin/supervise -c "conf/supervise/single-server/$PROFILE.conf"
