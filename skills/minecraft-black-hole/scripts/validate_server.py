"""Exercise real server processes and port ownership, including Windows address reuse."""
import json
import os
from pathlib import Path
import queue
import socket
import subprocess
import sys
import threading
import urllib.request


def main():
    script = Path(__file__).with_name('serve_preview.py')
    child_env = {**os.environ, 'PYTHONUTF8': '1'}
    first = subprocess.Popen([sys.executable, str(script), '--port', '0'],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', env=child_env)
    records = []
    try:
        lines = queue.Queue()
        threading.Thread(target=lambda: lines.put(first.stdout.readline()), daemon=True).start()
        url = lines.get(timeout=10).strip()
        if not url.startswith('http://127.0.0.1:'):
            raise RuntimeError(f'Start failed: {url}')
        port = int(url.rstrip('/').rsplit(':', 1)[1])
        records.append({'name': 'ephemeral_port', 'passed': port > 0})
        try:
            second = subprocess.run([sys.executable, str(script), '--port', str(port)],
                                    capture_output=True, text=True, encoding='utf-8', env=child_env, timeout=5)
            records.append({'name': 'duplicate_server_rejected',
                            'passed': second.returncode != 0 and 'Cannot open loopback port' in second.stderr,
                            'message': second.stderr.strip()})
        except subprocess.TimeoutExpired:
            records.append({'name': 'duplicate_server_rejected', 'passed': False, 'message': 'Second server remained running'})
        with socket.socket() as competing:
            competing.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                competing.bind(('127.0.0.1', port))
                rejected = False
            except OSError:
                rejected = True
            records.append({'name': 'reuseaddr_bind_rejected', 'passed': rejected})
        for i in range(3):
            with urllib.request.urlopen(url, timeout=5) as response:
                body = response.read().decode('utf-8')
                records.append({'name': f'original_service_intact_{i}',
                                'passed': response.status == 200 and '黑洞材质工作台' in body})
    finally:
        first.terminate()
        first.communicate(timeout=5)
    result = {'status': 'passed' if all(r['passed'] for r in records) else 'failed',
              'platform': sys.platform, 'checks': records}
    print(json.dumps(result, ensure_ascii=True, indent=2))
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    sys.exit(main())
