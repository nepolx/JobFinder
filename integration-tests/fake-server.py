from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse
import json
import threading

sent = []
lock = threading.Lock()
enabled = False

class Handler(BaseHTTPRequestHandler):
    def read_body(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size = int(self.rfile.readline().strip().split(b";", 1)[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            return b"".join(chunks)
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/sent":
            with lock:
                body = json.dumps({"messages": sent}).encode()
        elif path == "/api/v1/vacancies":
            date = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S+0000")
            vacancy = {"id":"11111111-1111-1111-1111-111111111111", "region":{"region_code":"7700000000000","name":"Москва"}, "company":{"name":"Test Employer"}, "job-name":"Integration Engineer", "salary_min":120000, "salary_max":160000, "schedule":"Полный рабочий день", "requirement":{"experience":3}, "vac_url":"https://example.test/vacancy/1", "date_modify":date}
            with lock:
                active = enabled
            body = json.dumps({"status":"200","meta":{"total":1 if active else 0,"limit":100},"results":{"vacancies":[{"vacancy":vacancy}] if active else []}}).encode()
        else:
            self.send_error(404)
            return
        self.send_response(200); self.send_header("Content-Type","application/json"); self.end_headers(); self.wfile.write(body)

    def do_POST(self):
        global enabled
        payload = json.loads(self.read_body())
        if self.path == "/enable":
            with lock:
                enabled = True
            result = {"ok": True}
        elif self.path.endswith("/sendMessage"):
            with lock:
                message_id = len(sent) + 1
                payload["message_id"] = message_id
                sent.append(payload)
            result = {"ok":True,"result":{"message_id":message_id}}
        else:
            result = {"ok":True,"result":True}
        body=json.dumps(result).encode(); self.send_response(200); self.send_header("Content-Type","application/json"); self.end_headers(); self.wfile.write(body)

    def log_message(self, *_): pass

ThreadingHTTPServer(("0.0.0.0",8090),Handler).serve_forever()
