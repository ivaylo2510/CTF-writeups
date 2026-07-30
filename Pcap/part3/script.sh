#!/usr/bin/env bash

IP="159.69.3.171"
PORTS=(6006 6005 6004 6003 6002 6009 6008)

echo "[*] Starting knock sequence on $IP ..."

for p in "${PORTS[@]}"; do
    echo "[*] Knocking on port $p ..."
    # -z = just scan / no data, -w 1 = 1s timeout so it doesn't hang
    nc -z -w 0.1 "$IP" "$p" >/dev/null 2>&1
done

echo "[*] Knock sequence done. Opening HTTPS on port 443 ..."

# Open URL in default browser (Linux desktop)
xdg-open "https://$IP:443/" >/dev/null 2>&1 &
