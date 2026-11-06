#!/usr/bin/env bash
# =====================================================================
# Instala Apache CouchDB en WSL (Ubuntu) como motor analitico de backup.
# Uso (desde Ubuntu en WSL):   bash install-couchdb.sh
# Credenciales: admin / admin123  ->  http://localhost:5984/_utils (Fauxton)
# =====================================================================
set -euo pipefail

COUCHDB_PASSWORD="${COUCHDB_PASSWORD:-admin123}"
COUCHDB_COOKIE="${COUCHDB_COOKIE:-facturacion-cookie}"
DATABASE="facturas_backup"

echo ">> 1/5 Dependencias"
sudo apt-get update -y
sudo apt-get install -y curl apt-transport-https gnupg ca-certificates lsb-release

echo ">> 2/5 Repositorio oficial de Apache CouchDB"
curl -fsSL https://couchdb.apache.org/repo/keys.asc | gpg --dearmor | \
  sudo tee /usr/share/keyrings/couchdb-archive-keyring.gpg > /dev/null
source /etc/os-release
echo "deb [signed-by=/usr/share/keyrings/couchdb-archive-keyring.gpg] https://apache.jfrog.io/artifactory/couchdb-deb/ ${VERSION_CODENAME} main" | \
  sudo tee /etc/apt/sources.list.d/couchdb.list > /dev/null
sudo apt-get update -y

echo ">> 3/5 Instalacion desatendida (modo standalone)"
sudo debconf-set-selections <<EOF
couchdb couchdb/mode select standalone
couchdb couchdb/mode seen true
couchdb couchdb/bindaddress string 0.0.0.0
couchdb couchdb/bindaddress seen true
couchdb couchdb/cookie string ${COUCHDB_COOKIE}
couchdb couchdb/cookie seen true
couchdb couchdb/adminpass password ${COUCHDB_PASSWORD}
couchdb couchdb/adminpass seen true
couchdb couchdb/adminpass_again password ${COUCHDB_PASSWORD}
couchdb couchdb/adminpass_again seen true
EOF
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y couchdb

echo ">> 4/5 Arranque del servicio"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "${SCRIPT_DIR}/start-couchdb.sh"

echo ">> 5/5 Base de datos de backup"
curl -s -X PUT "http://admin:${COUCHDB_PASSWORD}@127.0.0.1:5984/${DATABASE}" || true
echo
curl -s "http://admin:${COUCHDB_PASSWORD}@127.0.0.1:5984/"
echo
echo "Listo. Panel web: http://localhost:5984/_utils  (admin / ${COUCHDB_PASSWORD})"
