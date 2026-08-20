#!/usr/bin/env python3
"""Bridge locale GymFlow: NFC/ER750 collegato alla VPS tramite WebSocket."""
from __future__ import annotations

import json, re, socket, threading, time, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

try:
    import websocket
except ImportError as exc:
    raise SystemExit("Installa websocket-client: py -m pip install websocket-client") from exc

# CONFIGURAZIONE
VPS_URL = "https://app.tuodominio.it"
BRIDGE_KEY = "INSERISCI_QUI_LA_CHIAVE_DEL_BRIDGE"
GYM_ID = "00000000-0000-0000-0000-000000000001"
DEFAULT_DEVICE_ID = socket.gethostname()
LOCAL_HTTP_HOST, LOCAL_HTTP_PORT = "127.0.0.1", 8787
EVENT_SERVER_ENABLED, EVENT_SERVER_HOST, EVENT_SERVER_PORT = True, "0.0.0.0", 2169
EVENT_READ_TIMEOUT_SECONDS = 0.5
DOOR_READER_HOST, DOOR_READER_PORT = "169.254.40.235", 2167
REQUEST_TIMEOUT_SECONDS, DOOR_CONNECT_TIMEOUT_SECONDS, DOOR_READ_TIMEOUT_SECONDS = 8, 2, 1.5
RECONNECT_DELAY_SECONDS = 5

def ws_url() -> str:
    base = VPS_URL.rstrip("/").replace("https://", "wss://", 1).replace("http://", "ws://", 1)
    return base + "/ws/access-bridge"

def normalize_tag(value: str) -> str:
    return re.sub(r"[^A-Fa-f0-9]", "", value or "").upper()

def extract_tag(raw: bytes) -> str:
    text = raw.decode("utf-8", errors="ignore").strip()
    found = re.search(r"(?:TAG|UID|CARD|BADGE|NFC|RFID)\s*[:=]\s*([A-Fa-f0-9:\-\s]+)", text, re.I)
    return normalize_tag(found.group(1) if found else text) or normalize_tag(raw.hex())

def crc16(data: bytes, start: int = 1) -> int:
    crc = 0xFFFF
    for value in data[start:]:
        crc ^= value
        for _ in range(8): crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc & 0xFFFF

def open_door(seconds: int) -> None:
    seconds = max(1, min(int(seconds), 255))
    command = bytes([1, 0, 0x11, 2, 0, seconds]); checksum = crc16(command, 1)
    packet = command + bytes([checksum >> 8, checksum & 0xFF])
    with socket.create_connection((DOOR_READER_HOST, DOOR_READER_PORT), timeout=DOOR_CONNECT_TIMEOUT_SECONDS) as sock:
        sock.settimeout(DOOR_READ_TIMEOUT_SECONDS); sock.sendall(packet)
        try: print("ER750 response:", sock.recv(64).hex().upper())
        except socket.timeout: print("Comando apertura inviato a ER750")

class VpsBridge:
    def __init__(self) -> None:
        self.ws: websocket.WebSocketApp | None = None
        self.connected, self.lock = threading.Event(), threading.Lock()
        self.pending: dict[str, tuple[threading.Event, dict[str, Any]]] = {}

    def start(self) -> None: threading.Thread(target=self._run, daemon=True).start()

    def _run(self) -> None:
        while True:
            self.ws = websocket.WebSocketApp(ws_url(), on_open=self._opened, on_message=self._message,
                on_close=self._closed, on_error=lambda _ws, err: print(f"Errore WebSocket VPS: {err}"))
            self.ws.run_forever(ping_interval=30, ping_timeout=10)
            self.connected.clear(); time.sleep(RECONNECT_DELAY_SECONDS)

    def _opened(self, _ws: websocket.WebSocketApp) -> None:
        self.send({"type":"REGISTER", "apiKey":BRIDGE_KEY, "gymId":GYM_ID, "deviceId":DEFAULT_DEVICE_ID})

    def _closed(self, _ws: websocket.WebSocketApp, code: int | None, reason: str | None) -> None:
        print(f"WebSocket VPS disconnesso ({code}: {reason})")

    def _message(self, _ws: websocket.WebSocketApp, raw: str) -> None:
        try:
            payload = json.loads(raw); msg_type = payload.get("type")
            if msg_type == "REGISTERED":
                self.connected.set(); print(f"Bridge registrato per palestra {payload.get('gymId')}")
            elif msg_type == "SCAN_RESULT":
                with self.lock: pending = self.pending.get(payload.get("requestId", ""))
                if pending: pending[1].update(payload.get("result", {})); pending[0].set()
            elif msg_type == "OPEN_DOOR": self._remote_open(payload)
        except Exception as exc: print(f"Messaggio VPS non valido: {exc}")

    def _remote_open(self, payload: dict[str, Any]) -> None:
        command_id = payload.get("commandId", "")
        try:
            open_door(payload.get("relaySeconds", 3))
            self.send({"type":"COMMAND_RESULT", "commandId":command_id, "success":True})
        except Exception as exc:
            self.send({"type":"COMMAND_RESULT", "commandId":command_id, "success":False, "message":str(exc)})

    def send(self, payload: dict[str, Any]) -> None:
        with self.lock:
            if not self.ws or not self.ws.sock or not self.ws.sock.connected: raise RuntimeError("WebSocket VPS non connesso")
            self.ws.send(json.dumps(payload, ensure_ascii=False))

    def scan(self, tag_uid: str, device_ip: str) -> dict[str, Any]:
        tag_uid = normalize_tag(tag_uid)
        if not tag_uid: return {"granted":False, "command":"DENY", "message":"Tag vuoto o non valido"}
        if not self.connected.wait(REQUEST_TIMEOUT_SECONDS): return {"granted":False, "command":"DENY", "message":"VPS non connessa"}
        request_id, done, result = str(uuid.uuid4()), threading.Event(), {}
        with self.lock: self.pending[request_id] = (done, result)
        try:
            self.send({"type":"SCAN", "requestId":request_id, "tagUid":tag_uid, "deviceId":DEFAULT_DEVICE_ID, "deviceIp":device_ip})
            return result if done.wait(REQUEST_TIMEOUT_SECONDS) else {"granted":False, "command":"DENY", "message":"Timeout validazione VPS"}
        finally:
            with self.lock: self.pending.pop(request_id, None)

bridge = VpsBridge()

def process_scan(tag_uid: str, ip: str) -> dict[str, Any]:
    result = bridge.scan(tag_uid, ip)
    print(f"{time.strftime('%Y-%m-%d %H:%M:%S')} tag={tag_uid} command={result.get('command')} message={result.get('message', '')}")
    if result.get("granted") and result.get("command") == "OPEN": open_door(result.get("relaySeconds") or 3)
    return result

class LocalApi(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        self.reply(200, {"status":"UP", "vpsConnected":bridge.connected.is_set()}) if self.path == "/health" else self.reply(404, {"error":"Not found"})
    def do_POST(self) -> None:
        if self.path != "/scan": self.reply(404, {"error":"Not found"}); return
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))) or "{}")
            self.reply(200, process_scan(body.get("tagUid") or body.get("tag") or body.get("uid") or "", self.client_address[0]))
        except Exception as exc: self.reply(500, {"granted":False, "command":"DENY", "message":str(exc)})
    def log_message(self, _format: str, *args: Any) -> None: pass
    def reply(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode(); self.send_response(status); self.send_header("Content-Type","application/json; charset=utf-8"); self.send_header("Content-Length",str(len(body))); self.end_headers(); self.wfile.write(body)

def event_client(conn: socket.socket, address: tuple[str, int]) -> None:
    with conn:
        buffer = b""; conn.settimeout(EVENT_READ_TIMEOUT_SECONDS)
        while True:
            try:
                chunk = conn.recv(1024)
                if not chunk: return
                buffer += chunk
                while b"\n" in buffer:
                    line, buffer = buffer.split(b"\n", 1); result = process_scan(extract_tag(line), address[0]); conn.sendall(f"{result.get('command', 'DENY')}:{result.get('message', '')}\n".encode())
            except socket.timeout:
                if buffer:
                    result = process_scan(extract_tag(buffer), address[0]); buffer = b""; conn.sendall(f"{result.get('command', 'DENY')}:{result.get('message', '')}\n".encode())

def event_server() -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM); server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); server.bind((EVENT_SERVER_HOST, EVENT_SERVER_PORT)); server.listen(20)
    print(f"Event server attivo su {EVENT_SERVER_HOST}:{EVENT_SERVER_PORT}")
    while True:
        conn, address = server.accept(); threading.Thread(target=event_client, args=(conn, address), daemon=True).start()

def main() -> None:
    bridge.start()
    if EVENT_SERVER_ENABLED: threading.Thread(target=event_server, daemon=True).start()
    print(f"Bridge locale: http://{LOCAL_HTTP_HOST}:{LOCAL_HTTP_PORT}; VPS: {ws_url()}")
    ThreadingHTTPServer((LOCAL_HTTP_HOST, LOCAL_HTTP_PORT), LocalApi).serve_forever()

if __name__ == "__main__": main()
