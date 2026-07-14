#!/usr/bin/env python3
"""
Server locale per ponte accessi GymFlow.

Flusso:
1. Il lettore/programma locale invia un tag a questo server.
2. Questo server chiama la VPS: POST /api/access-bridge/validate.
3. La VPS decide, registra l'accesso e risponde OPEN oppure DENY.
4. Questo server esegue il comando locale di apertura porta solo se riceve OPEN.

Variabili ambiente principali:
  GYMFLOW_VPS_URL=https://app.tuodominio.it
  GYMFLOW_BRIDGE_KEY=chiave_uguale_a_ACCESS_BRIDGE_API_KEY_sul_vps
  LOCAL_HTTP_HOST=127.0.0.1
  LOCAL_HTTP_PORT=8787

Opzioni apertura porta, scegline una:
  DOOR_OPEN_URL=http://127.0.0.1:5000/open
  DOOR_COMMAND=C:\\path\\to\\open-door.exe
  DOOR_TCP_HOST=192.168.1.50
  DOOR_TCP_PORT=2169
  DOOR_TCP_OPEN_PAYLOAD=OPEN

Opzionale listener TCP per lettore locale:
  READER_TCP_ENABLED=false
  READER_TCP_HOST=0.0.0.0
  READER_TCP_PORT=2169
"""

from __future__ import annotations

import json
import os
import re
import shlex
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


VPS_URL = os.getenv("GYMFLOW_VPS_URL", "https://app.tuodominio.it").rstrip("/")
BRIDGE_KEY = os.getenv("GYMFLOW_BRIDGE_KEY", "")
LOCAL_HTTP_HOST = os.getenv("LOCAL_HTTP_HOST", "127.0.0.1")
LOCAL_HTTP_PORT = int(os.getenv("LOCAL_HTTP_PORT", "8787"))
DEFAULT_DEVICE_ID = os.getenv("DEVICE_ID", socket.gethostname())
REQUEST_TIMEOUT_SECONDS = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "8"))

DOOR_OPEN_URL = os.getenv("DOOR_OPEN_URL", "").strip()
DOOR_OPEN_URL_METHOD = os.getenv("DOOR_OPEN_URL_METHOD", "POST").upper()
DOOR_COMMAND = os.getenv("DOOR_COMMAND", "").strip()
DOOR_TCP_HOST = os.getenv("DOOR_TCP_HOST", "").strip()
DOOR_TCP_PORT = int(os.getenv("DOOR_TCP_PORT", "2169"))
DOOR_TCP_OPEN_PAYLOAD = os.getenv("DOOR_TCP_OPEN_PAYLOAD", "OPEN").encode("utf-8")

READER_TCP_ENABLED = os.getenv("READER_TCP_ENABLED", "false").lower() == "true"
READER_TCP_HOST = os.getenv("READER_TCP_HOST", "0.0.0.0")
READER_TCP_PORT = int(os.getenv("READER_TCP_PORT", "2169"))


def normalize_tag(value: str) -> str:
    return re.sub(r"[^A-Fa-f0-9]", "", value or "").upper()


def extract_tag(raw: str) -> str:
    text = raw.strip()
    keyed = re.search(r"(?:TAG|UID|CARD|BADGE|NFC|RFID)\s*[:=]\s*([A-Fa-f0-9:\-\s]+)", text, re.I)
    if keyed:
        return normalize_tag(keyed.group(1))
    return normalize_tag(text)


def post_to_vps(tag_uid: str, device_id: str, device_ip: str) -> dict[str, Any]:
    if not BRIDGE_KEY:
        raise RuntimeError("GYMFLOW_BRIDGE_KEY non configurata")

    payload = json.dumps({
        "tagUid": tag_uid,
        "deviceId": device_id,
        "deviceIp": device_ip,
    }).encode("utf-8")

    request = urllib.request.Request(
        f"{VPS_URL}/api/access-bridge/validate",
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Access-Bridge-Key": BRIDGE_KEY,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Errore VPS {exc.code}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"VPS non raggiungibile: {exc.reason}") from exc


def open_door(relay_seconds: int | None = None) -> None:
    if DOOR_OPEN_URL:
        data = json.dumps({"seconds": relay_seconds or 3}).encode("utf-8")
        request = urllib.request.Request(
            DOOR_OPEN_URL,
            data=data if DOOR_OPEN_URL_METHOD != "GET" else None,
            method=DOOR_OPEN_URL_METHOD,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            response.read()
        return

    if DOOR_COMMAND:
        args = shlex.split(DOOR_COMMAND, posix=os.name != "nt")
        subprocess.run(args, check=True, timeout=10)
        return

    if DOOR_TCP_HOST:
        payload = DOOR_TCP_OPEN_PAYLOAD
        if not payload.endswith(b"\n"):
            payload += b"\n"
        with socket.create_connection((DOOR_TCP_HOST, DOOR_TCP_PORT), timeout=5) as sock:
            sock.sendall(payload)
        return

    print("OPEN ricevuto ma nessun comando porta configurato")


def process_scan(tag_uid: str, device_id: str, device_ip: str) -> dict[str, Any]:
    tag_uid = normalize_tag(tag_uid)
    if not tag_uid:
        return {"granted": False, "command": "DENY", "message": "Tag vuoto o non valido"}

    result = post_to_vps(tag_uid, device_id, device_ip)
    print(f"{time.strftime('%Y-%m-%d %H:%M:%S')} tag={tag_uid} command={result.get('command')} message={result.get('message')}")

    if result.get("command") == "OPEN" and result.get("granted") is True:
        open_door(result.get("relaySeconds"))

    return result


class BridgeHttpHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path == "/health":
            self.send_json(200, {"status": "UP"})
            return
        self.send_json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        if self.path != "/scan":
            self.send_json(404, {"error": "Not found"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length).decode("utf-8")
            payload = json.loads(body or "{}")
            tag_uid = payload.get("tagUid") or payload.get("tag") or payload.get("uid") or ""
            device_id = payload.get("deviceId") or DEFAULT_DEVICE_ID
            result = process_scan(tag_uid, device_id, self.client_address[0])
            self.send_json(200, result)
        except Exception as exc:
            self.send_json(500, {"granted": False, "command": "DENY", "message": str(exc)})

    def log_message(self, format: str, *args: Any) -> None:
        return

    def send_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def handle_reader_client(conn: socket.socket, address: tuple[str, int]) -> None:
    with conn:
        buffer = b""
        while True:
            chunk = conn.recv(1024)
            if not chunk:
                return
            buffer += chunk
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                raw = line.decode("utf-8", errors="replace").strip()
                tag_uid = extract_tag(raw)
                try:
                    result = process_scan(tag_uid, DEFAULT_DEVICE_ID, address[0])
                    response = f"{result.get('command', 'DENY')}:{result.get('message', '')}\n"
                except Exception as exc:
                    response = f"DENY:{exc}\n"
                conn.sendall(response.encode("utf-8"))


def start_reader_tcp_server() -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((READER_TCP_HOST, READER_TCP_PORT))
    server.listen(20)
    print(f"Reader TCP attivo su {READER_TCP_HOST}:{READER_TCP_PORT}")
    while True:
        conn, address = server.accept()
        threading.Thread(target=handle_reader_client, args=(conn, address), daemon=True).start()


def main() -> None:
    if READER_TCP_ENABLED:
        threading.Thread(target=start_reader_tcp_server, daemon=True).start()

    httpd = ThreadingHTTPServer((LOCAL_HTTP_HOST, LOCAL_HTTP_PORT), BridgeHttpHandler)
    print(f"GymFlow bridge locale attivo su http://{LOCAL_HTTP_HOST}:{LOCAL_HTTP_PORT}")
    print(f"VPS: {VPS_URL}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
