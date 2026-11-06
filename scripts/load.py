#!/usr/bin/env python3
"""Small local concurrency check, not a production benchmark. Creates one retained test link."""
import concurrent.futures
import json
import os
import secrets
import statistics
import time
import urllib.request
import urllib.error

BASE = os.environ.get('BASE_URL', 'http://localhost:8080')
KEY = os.environ['SHORTENER_API_KEY']
N = int(os.environ.get('LOAD_REQUESTS', '200'))
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def call(method, path, data=None):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    headers = {'X-API-Key': KEY, 'Content-Type': 'application/json'} if path.startswith('/api/') else {}
    req = urllib.request.Request(BASE + path, headers=headers, method=method,
                                 data=json.dumps(data).encode() if data is not None else None)
    start = time.perf_counter()
    try:
        response = opener.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    body = response.read()
    elapsed = (time.perf_counter() - start) * 1000
    return response.code, body, elapsed

alias = 'load' + secrets.token_hex(6)
status, _, _ = call('POST', '/api/v1/links', {'url': 'https://example.com/load', 'customAlias': alias})
assert status == 201
start = time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    results = list(pool.map(lambda _: call('GET', '/s/' + alias), range(N)))
elapsed = time.perf_counter() - start
assert all(result[0] == 302 for result in results)
status, body, _ = call('GET', '/api/v1/links/' + alias)
assert status == 200 and json.loads(body)['clicks'] == N
latencies = sorted(result[2] for result in results)
print(json.dumps({'requests': N, 'workers': 8, 'errors': 0, 'recordedClicks': N,
                  'elapsedSeconds': round(elapsed, 3), 'requestsPerSecond': round(N / elapsed, 1),
                  'medianMs': round(statistics.median(latencies), 2),
                  'p95Ms': round(latencies[max(0, int(.95 * N) - 1)], 2)}, indent=2))
