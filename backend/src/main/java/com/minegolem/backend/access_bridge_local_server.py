#!/usr/bin/env python3
"""
Bridge locale GymFlow per lettore NFC/ER750.

Flusso:
1. Il PC locale riceve una scansione NFC via HTTP o TCP.
2. Il PC locale chiede alla VPS se il tag puo' entrare.
3. La VPS risponde OPEN o DENY.
4. Solo se la risposta e' OPEN, questo script apre la porta sull'ER750.

Modifica i valori nella sezione CONFIGURAZIONE, senza usare file .env.
"""

from __future__ import annotations

import json
import re
import socket
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


# =========================
# CONFIGURAZIONE
# =========================

# URL pubblico del backend sulla VPS, senza slash finale.
VPS_URL = "https://app.tuodominio.it"

# Deve essere uguale ad access.bridge.api-key configurata nel backend sulla VPS.
BRIDGE_KEY = "INSERISCI_QUI_LA_CHIAVE_DEL_BRIDGE"

# Server HTTP locale usato per ricevere scansioni da programmi/lettori locali.
LOCAL_HTTP_HOST = "127.0.0.1"
LOCAL_HTTP_PORT = 8787

# Nome dispositivo mostrato nei log accessi.
DEFAULT_DEVICE_ID = socket.gethostname()

# Lettore/centralina ER750 da comandare per aprire la porta.
ER750_HOST = "169.254.40.235"
ER750_PORT = 2167

# Timeout connessioni.
REQUEST_TIMEOUT_SECONDS = 8
ER750_CONNECT_TIMEOUT_SECONDS = 2
ER750_READ_TIMEOUT_SECONDS = 1.5

# Listener TCP opzionale per lettori che inviano righe di testo.
READER_TCP_ENABLED = False
READER_TCP_HOST = "0.0.0.0"
READER_TCP_PORT = 2169


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
        raise RuntimeError("BRIDGE_KEY non configurata nella sezione CONFIGURAZIONE")

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


def crc16(data: bytes, start: int = 1) -> int:
    crc = 0xFFFF
    for byte in data[start:]:
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc & 0xFFFF


def build_er750_open_packet(seconds: int) -> bytes:
    seconds = max(1, min(int(seconds), 255))
    command = bytes([0x01, 0x00, 0x11, 0x02, 0x00, seconds])
    checksum = crc16(command, 1)
    return command + bytes([(checksum >> 8) & 0xFF, checksum & 0xFF])


def open_er750_door(seconds: int) -> None:
    packet = build_er750_open_packet(seconds)
    with socket.create_connection(
        (ER750_HOST, ER750_PORT),
        timeout=ER750_CONNECT_TIMEOUT_SECONDS,
    ) as sock:
        sock.settimeout(ER750_READ_TIMEOUT_SECONDS)
        sock.sendall(packet)

        try:
            response = sock.recv(64)
            if response:
                print(f"ER750 response: {response.hex().upper()}")
        except socket.timeout:
            print("Comando apertura inviato a ER750, nessuna risposta prima del timeout")


def process_scan(tag_uid: str, device_id: str, device_ip: str) -> dict[str, Any]:
    tag_uid = normalize_tag(tag_uid)
    if not tag_uid:
        return {"granted": False, "command": "DENY", "message": "Tag vuoto o non valido"}

    result = post_to_vps(tag_uid, device_id, device_ip)
    command = result.get("command", "DENY")
    granted = result.get("granted") is True
    relay_seconds = result.get("relaySeconds") or 3

    print(
        f"{time.strftime('%Y-%m-%d %H:%M:%S')} "
        f"tag={tag_uid} device={device_id} command={command} message={result.get('message', '')}"
    )

    if granted and command == "OPEN":
        open_er750_door(relay_seconds)

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
    print(f"ER750: {ER750_HOST}:{ER750_PORT}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
