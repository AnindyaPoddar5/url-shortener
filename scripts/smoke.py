#!/usr/bin/env python3
"""Real HTTP acceptance checks. Requires a running server and SHORTENER_API_KEY."""
import json
import os
import secrets
import urllib.request
import urllib.error

BASE = os.environ.get('BASE_URL', 'http://localhost:8080')
KEY = os.environ['SHORTENER_API_KEY']
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
def request(method, path, status, data=None, key=True):
    headers = {'X-API-Key': KEY} if key else {}
    if data is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=json.dumps(data).encode() if data is not None else None,
                                 headers=headers, method=method)
    try:
        res = opener.open(req, timeout=10)
    except urllib.error.HTTPError as error:
        res = error
    body = res.read()
    assert res.code == status, (method, path, res.code, status, body)
    return json.loads(body) if body and res.headers.get('Content-Type', '').startswith('application/json') else body, res.headers

alias = 'smoke' + secrets.token_hex(5)
request('GET', '/actuator/health', 200, key=False)
created, _ = request('POST', '/api/v1/links', 201, {'url': 'https://example.com/smoke', 'customAlias': alias})
assert created['code'] == alias
_, headers = request('GET', '/s/' + alias, 302, key=False)
assert headers['Location'] == 'https://example.com/smoke'
request('HEAD', '/s/' + alias, 302, key=False)
analytics, _ = request('GET', '/api/v1/links/' + alias, 200)
assert analytics['clicks'] == 1
request('GET', '/api/v1/links/' + alias, 401, key=False)
request('POST', '/api/v1/links', 409, {'url': 'https://example.com/other', 'customAlias': alias})
request('PATCH', '/api/v1/links/' + alias + '/expiration', 200, {'expiresAt': '2099-01-01T00:00:00Z'})
request('DELETE', '/api/v1/links/' + alias, 204)
request('DELETE', '/api/v1/links/' + alias, 204)
request('GET', '/s/' + alias, 410, key=False)
request('GET', '/s/nonexistent-smoke-code', 404, key=False)
print('PASS: packaged HTTP lifecycle, analytics, HEAD, authentication, conflicts, expiry update, disable and missing-link checks')
