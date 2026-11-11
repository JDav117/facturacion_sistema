#!/usr/bin/env bash
# Detiene todos los servicios de Apache Druid iniciados con start-druid.sh
pkill -f "bin/supervise" 2>/dev/null
pkill -f "org.apache.druid.cli.Main" 2>/dev/null
sleep 3
if pgrep -f "org.apache.druid.cli.Main" > /dev/null; then
  pkill -9 -f "org.apache.druid.cli.Main"
fi
echo "Druid detenido."
