#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""冒烟测试：健康检查 + 登录 + bootstrap，不修改任何数据。

用法：
    python tools/smoke.py
    python tools/smoke.py --base http://127.0.0.1:8080 --account 7 --pin 123456
"""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--account", default="7")
    parser.add_argument("--pin", default="123456")
    args = parser.parse_args()

    base = args.base.rstrip("/")
    try:
        with urllib.request.urlopen(base + "/api/health", timeout=10) as response:
            health = json.loads(response.read().decode("utf-8"))
        print(f"[ok] health: {health}")
    except Exception as exc:  # noqa: BLE001
        print(f"[fail] health: {exc}")
        return 1

    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    body = json.dumps({"account": args.account, "pin": args.pin}).encode("utf-8")
    request = urllib.request.Request(
        base + "/api/login",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with opener.open(request, timeout=15) as response:
            login = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        print(f"[fail] login: HTTP {exc.code} {exc.read().decode('utf-8', 'ignore')}")
        return 1

    user = login.get("user", {})
    print(f"[ok] login: {user.get('id')} / {user.get('name')} / admin={user.get('isAdmin')}")

    try:
        with opener.open(base + "/api/bootstrap", timeout=20) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        print(f"[fail] bootstrap: HTTP {exc.code}")
        return 1

    print(
        "[ok] bootstrap: "
        f"employees={len(data.get('employees', []))} "
        f"jobs={len(data.get('jobs', []))} "
        f"records={len(data.get('records', []))}"
    )

    required = {"user", "employees", "skills", "routes", "jobs", "records", "corrections", "settings"}
    missing = required - data.keys()
    if missing:
        print(f"[fail] bootstrap missing keys: {sorted(missing)}")
        return 1

    print("[ok] contract keys present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
