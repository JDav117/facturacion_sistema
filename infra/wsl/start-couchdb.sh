#!/usr/bin/env bash
# Arranca CouchDB en WSL, con o sin systemd, y espera a que responda.
set -uo pipefail

if curl -s http://127.0.0.1:5984/_up > /dev/null 2>&1; then
  echo "CouchDB ya esta corriendo."
  exit 0
fi

if [ "$(ps -p 1 -o comm= 2>/dev/null)" = "systemd" ]; then
  sudo systemctl enable couchdb > /dev/null 2>&1 || true
  sudo systemctl start couchdb
else
  # WSL sin systemd: se lanza el binario en segundo plano
  sudo -u couchdb nohup /opt/couchdb/bin/couchdb > /tmp/couchdb.log 2>&1 &
fi

for i in $(seq 1 30); do
  if curl -s http://127.0.0.1:5984/_up > /dev/null 2>&1; then
    echo "CouchDB arriba en http://localhost:5984"
    exit 0
  fi
  sleep 1
done

echo "CouchDB no respondio en 30 s. Revise: sudo journalctl -u couchdb  o  /tmp/couchdb.log"
exit 1
