#!/usr/bin/env python3
"""Deterministic stateful stub of the Keycloak admin API for reconciler unit tests.

Serves exactly the endpoints scripts/operator/keycloak-mfa-reconcile.sh touches.
State starts from fixtures/initial-state.json and mutates in memory on PUT/POST so
idempotency is real, not simulated. Every request is appended to the request log
(method, path, body) so the test runner can prove what the reconciler did and,
crucially, what it never did (no DELETE, no credential writes).
"""
import json
import os
import re
import sys
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STATE_FILE = sys.argv[1]
REQUEST_LOG = sys.argv[2]
PORT = int(sys.argv[3])

with open(STATE_FILE) as fh:
    STATE = json.load(fh)
LOCK = threading.Lock()

EXPECTED_CLIENT_ID = os.environ.get("STUB_RECONCILER_CLIENT_ID", "impilo-realm-reconciler")
EXPECTED_CLIENT_SECRET = os.environ.get("STUB_RECONCILER_CLIENT_SECRET", "unit-test-reconciler-secret")


def log_request(method, path, body):
    with LOCK, open(REQUEST_LOG, "a") as fh:
        fh.write(json.dumps({"method": method, "path": path, "body": body}) + "\n")


def dump_state():
    with LOCK, open(STATE_FILE + ".live", "w") as fh:
        json.dump(STATE, fh, indent=1, sort_keys=True)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length).decode() if length else ""
        try:
            return json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return raw

    def _send(self, status, payload=None):
        body = b"" if payload is None else json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    # ── routing ────────────────────────────────────────────────────────────
    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        body = self._read_body()
        log_request("POST", path, body if isinstance(body, (dict, list)) else "<form>")

        if path.endswith("/protocol/openid-connect/token"):
            form = urllib.parse.parse_qs(body if isinstance(body, str) else "")
            if (form.get("client_id", [""])[0] == EXPECTED_CLIENT_ID
                    and form.get("client_secret", [""])[0] == EXPECTED_CLIENT_SECRET
                    and form.get("grant_type", [""])[0] == "client_credentials"):
                self._send(200, {"access_token": "stub-admin-token", "expires_in": 300})
            else:
                self._send(401, {"error": "invalid_client"})
            return

        match = re.match(r"^/admin/realms/impilo/users/([^/]+)/role-mappings/clients/([^/]+)$", path)
        if match:
            user_id, client_uuid = match.groups()
            grants = STATE.setdefault("client_role_mappings", {}).setdefault(user_id, {}).setdefault(client_uuid, [])
            for role in (body or []):
                if role["name"] not in [g["name"] for g in grants]:
                    grants.append({"id": role["id"], "name": role["name"]})
            dump_state()
            self._send(204)
            return

        if path == "/admin/realms/impilo/clients":
            STATE["clients"].append({
                "id": "generated-" + body["clientId"], "clientId": body["clientId"],
                **{k: v for k, v in body.items() if k != "clientId"}})
            dump_state()
            self._send(201)
            return

        if path == "/admin/realms/impilo/authentication/register-required-action":
            STATE["required_actions"].append({
                "alias": body["providerId"], "name": body.get("name", body["providerId"]),
                "enabled": True, "defaultAction": False})
            dump_state()
            self._send(204)
            return

        # Flow/execution creation endpoints — accepted but the fixture flow is
        # already governed, so the reconciler never reaches these in these tests.
        self._send(201)

    def do_PUT(self):
        path = urllib.parse.urlparse(self.path).path
        body = self._read_body()
        log_request("PUT", path, body)

        if path == "/admin/realms/impilo":
            STATE["realm"] = body
            dump_state()
            self._send(204)
            return

        match = re.match(r"^/admin/realms/impilo/clients/([^/]+)$", path)
        if match:
            uuid = match.group(1)
            for index, client in enumerate(STATE["clients"]):
                if client["id"] == uuid:
                    STATE["clients"][index] = body
            dump_state()
            self._send(204)
            return

        match = re.match(r"^/admin/realms/impilo/users/([^/]+)$", path)
        if match:
            uid = match.group(1)
            for index, user in enumerate(STATE["users"]):
                if user["id"] == uid:
                    STATE["users"][index] = body
            dump_state()
            self._send(204)
            return

        match = re.match(r"^/admin/realms/impilo/authentication/required-actions/([^/]+)$", path)
        if match:
            alias = match.group(1)
            for index, action in enumerate(STATE["required_actions"]):
                if action["alias"] == alias:
                    STATE["required_actions"][index] = body
            dump_state()
            self._send(204)
            return

        self._send(204)

    def do_DELETE(self):
        path = urllib.parse.urlparse(self.path).path
        log_request("DELETE", path, None)
        # The reconciler must never delete anything. Refuse loudly so a regression
        # is a hard failure, and the request log records the violation.
        self._send(500, {"error": "STUB_REFUSES_DELETE"})

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        log_request("GET", path, None)

        if path == "/admin/realms/impilo":
            self._send(200, STATE["realm"])
            return
        if path == "/admin/realms/impilo/users/count":
            self._send(200, STATE["user_count"])
            return
        if path == "/admin/realms/impilo/clients":
            self._send(200, STATE["clients"])
            return
        match = re.match(r"^/admin/realms/impilo/clients/([^/]+)/service-account-user$", path)
        if match:
            self._send(200, {"id": "sa-" + match.group(1)})
            return
        match = re.match(r"^/admin/realms/impilo/clients/([^/]+)/roles$", path)
        if match:
            self._send(200, STATE["management_roles"])
            return
        match = re.match(r"^/admin/realms/impilo/clients/([^/]+)$", path)
        if match:
            for client in STATE["clients"]:
                if client["id"] == match.group(1):
                    self._send(200, client)
                    return
            self._send(404, {})
            return
        if path == "/admin/realms/impilo/authentication/required-actions":
            self._send(200, STATE["required_actions"])
            return
        if path == "/admin/realms/impilo/authentication/unregistered-required-actions":
            self._send(200, STATE.get("unregistered_required_actions", []))
            return
        if path == "/admin/realms/impilo/authentication/flows":
            self._send(200, [{"alias": alias} for alias in STATE["flows"]])
            return
        match = re.match(r"^/admin/realms/impilo/authentication/flows/([^/]+)/executions$", path)
        if match:
            alias = urllib.parse.unquote(match.group(1))
            self._send(200, STATE["flows"].get(alias, []))
            return
        if path == "/admin/realms/impilo/users":
            query = urllib.parse.parse_qs(parsed.query)
            first = int(query.get("first", ["0"])[0])
            maximum = int(query.get("max", ["100"])[0])
            self._send(200, STATE["users"][first:first + maximum])
            return
        match = re.match(r"^/admin/realms/impilo/users/([^/]+)/role-mappings/realm/composite$", path)
        if match:
            self._send(200, STATE["user_realm_roles"].get(match.group(1), []))
            return
        match = re.match(r"^/admin/realms/impilo/users/([^/]+)/role-mappings/clients/([^/]+)/composite$", path)
        if match:
            user_id, client_uuid = match.groups()
            self._send(200, STATE.get("client_role_mappings", {}).get(user_id, {}).get(client_uuid, []))
            return
        match = re.match(r"^/admin/realms/impilo/users/([^/]+)/credentials$", path)
        if match:
            self._send(200, STATE["user_credentials"].get(match.group(1), []))
            return
        match = re.match(r"^/admin/realms/impilo/users/([^/]+)$", path)
        if match:
            for user in STATE["users"]:
                if user["id"] == match.group(1):
                    self._send(200, user)
                    return
            self._send(404, {})
            return
        self._send(404, {"error": "unhandled path " + path})


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    dump_state()
    server.serve_forever()
