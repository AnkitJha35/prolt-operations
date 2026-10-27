#!/usr/bin/env python3
"""
Staging End-to-End Smoke Test Suite
Executes live against the isolated local staging deployment (http://127.0.0.1:8089).
Never prints passwords, hashes, or credentials.
"""

import sys
import os
import json
import time
import urllib.request
import urllib.error

BASE_URL = os.environ.get("STAGING_BASE_URL", "http://127.0.0.1:8089")
ADMIN_USER = os.environ.get("ADMIN_USERNAME", "stagingadmin")
ADMIN_PASS = os.environ.get("ADMIN_PASSWORD", "StagingAdminPass2026!")

results = []

def record(test_name, status, status_code, latency_ms, details=""):
    results.append({
        "test": test_name,
        "status": status,
        "http_code": status_code,
        "latency_ms": round(latency_ms, 2),
        "details": details
    })
    mark = "PASS" if status == "PASS" else "FAIL"
    print(f"[{mark}] {test_name:<45} HTTP {status_code:<4} {latency_ms:>6.1f}ms - {details}")

def request(method, path, body=None, headers=None):
    url = f"{BASE_URL}{path}"
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    
    data = None
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body).encode("utf-8")
        elif isinstance(body, bytes):
            data = body
        elif isinstance(body, str):
            data = body.encode("utf-8")
            
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            latency = (time.time() - start) * 1000
            content = resp.read().decode("utf-8", errors="replace")
            parsed = None
            try:
                parsed = json.loads(content)
            except Exception:
                pass
            return resp.status, parsed or content, latency
    except urllib.error.HTTPError as e:
        latency = (time.time() - start) * 1000
        content = e.read().decode("utf-8", errors="replace")
        parsed = None
        try:
            parsed = json.loads(content)
        except Exception:
            pass
        return e.code, parsed or content, latency
    except Exception as e:
        latency = (time.time() - start) * 1000
        return 0, str(e), latency

def run_tests():
    print("=" * 80)
    print("RUNNING PROLT-OPERATIONS LOCAL STAGING SMOKE TEST SUITE")
    print(f"Target: {BASE_URL}")
    print("=" * 80)

    # 1. Health & Actuator
    code, body, lat = request("GET", "/actuator/health")
    if code == 200 and isinstance(body, dict) and body.get("status") == "UP":
        db_st = body.get("components", {}).get("db", {}).get("status")
        redis_st = body.get("components", {}).get("redis", {}).get("status")
        record("Actuator Health (DB + Redis)", "PASS", code, lat, f"status=UP, db={db_st}, redis={redis_st}")
    else:
        record("Actuator Health (DB + Redis)", "FAIL", code, lat, str(body))

    # 2. Authentication & Authorization
    # 2a. Bad password
    code, body, lat = request("POST", "/api/auth/login", {"username": ADMIN_USER, "password": "WrongPassword123!"})
    if code == 401:
        record("Auth: Invalid Credentials", "PASS", code, lat, "Rejected with 401")
    else:
        record("Auth: Invalid Credentials", "FAIL", code, lat, f"Expected 401, got {code}")

    # 2b. Valid login
    code, body, lat = request("POST", "/api/auth/login", {"username": ADMIN_USER, "password": ADMIN_PASS})
    token = None
    if code == 200 and isinstance(body, dict) and body.get("success") and "token" in body:
        token = body["token"]
        record("Auth: Admin Login (JWT)", "PASS", code, lat, f"Role: {body.get('user', {}).get('role')}")
    else:
        record("Auth: Admin Login (JWT)", "FAIL", code, lat, str(body))
        print("FATAL: Cannot proceed without valid JWT token")
        return

    auth_header = {"Authorization": f"Bearer {token}"}

    # 2c. Get /me without token
    code, body, lat = request("GET", "/api/auth/me")
    if code in (401, 403):
        record("Auth: Unauthenticated /api/auth/me", "PASS", code, lat, f"Protected (HTTP {code})")
    else:
        record("Auth: Unauthenticated /api/auth/me", "FAIL", code, lat, f"Expected 401/403, got {code}")

    # 2d. Get /me with token
    code, body, lat = request("GET", "/api/auth/me", headers=auth_header)
    username = body.get("user", {}).get("username") if isinstance(body, dict) and "user" in body else body.get("username")
    role = body.get("user", {}).get("role") if isinstance(body, dict) and "user" in body else body.get("role")
    if code == 200 and username == ADMIN_USER:
        record("Auth: Authenticated /api/auth/me", "PASS", code, lat, f"User: {username}, Role: {role}")
    else:
        record("Auth: Authenticated /api/auth/me", "FAIL", code, lat, str(body))

    # 2e. RBAC: Super Admin route (/api/users) accessed by ADMIN
    code, body, lat = request("GET", "/api/users", headers=auth_header)
    if code == 403:
        record("RBAC: Admin forbidden on Super Admin route", "PASS", code, lat, "HTTP 403 Forbidden")
    else:
        record("RBAC: Admin forbidden on Super Admin route", "FAIL", code, lat, f"Expected 403, got {code}")

    # 3. Resource Center CRUD (All 9 Models)
    crud_specs = [
        {
            "type": "region-state",
            "create": {"regionalGrid": "STG_WEST", "regionCode": "STG_WR", "regionName": "STG West", "stateName": "STG State", "stateCode": "STG", "stateOrUt": "State"},
            "update": {"stateName": "STG State Updated"}
        },
        {
            "type": "discom-list",
            "create": {"code": "STG_DISCOM_1", "legalName": "STG Power Co", "stateCode": "STG", "discomType": "Private"},
            "update": {"legalName": "STG Power Corporation"}
        },
        {
            "type": "ists-charges",
            "create": {"istsLossPercent": 3.75, "startDate": "2026-11-01", "endDate": "2026-11-30"},
            "update": {"istsLossPercent": 4.10}
        },
        {
            "type": "iex-fees",
            "create": {"month": "2026-11", "exchangeFees": 0.02, "exchangeFeesGst": 18.0, "nldcApplicationFees": 5000, "nldcSchedulingFees": 1000, "sldcSchedulingFees": 1000, "otherFixCharges": 500},
            "update": {"exchangeFees": 0.025}
        },
        {
            "type": "prolt-margin",
            "create": {"month": "2026-11", "customerId": "STG_CUST_1", "tradingMargin": 0.04, "tradingMarginGst": 18.0, "proltMargin": 0.02, "proltMarginGst": 18.0},
            "update": {"tradingMargin": 0.05}
        },
        {
            "type": "ctu-charges",
            "create": {"state": "STG_STATE", "month": "2026-11", "ctuChargesRsPerKwh": 0.45},
            "update": {"ctuChargesRsPerKwh": 0.52},
            "verify_json": "ctu_charges_rs_per_kwh"
        },
        {
            "type": "state-charges",
            "create": {"state": "STG_STATE", "discom": "STG_DISCOM_1", "category": "HT", "subCategory": "Ind", "supplyVoltageCategory": "11kV", "voltageLevel": "11kV", "fromDate": "2026-11-01", "toDate": "2026-11-30", "demandFixedChargeKvaPerMonthRs": 150.0, "crossSubsidy": 1.25, "distributionWheelingCharges": 0.4, "stuCharges": 0.2, "stuLossPercent": 2.1, "wheelingLossPercent": 3.5, "additionalCharge": 0.1},
            "update": {"demandFixedChargeKvaPerMonthRs": 160.0}
        },
        {
            "type": "state-tariff",
            "create": {"state": "STG_STATE", "discom": "STG_DISCOM_1", "consumerCategory": "HT", "subCategory": "Ind", "supplyVoltageCategory": "11kV", "supplyVoltage": "11kV", "month": "2026-11", "consumptionMonth": "2026-11", "todStartTime": "06:00", "todEndTime": "10:00", "baseEnergyRate": 5.50, "baseEnergyUnit": "kWh", "todChargePercent": 20, "energyRate": 6.60},
            "update": {"energyRate": 6.75}
        },
        {
            "type": "fppa-charges",
            "create": {"state": "STG_STATE", "discom": "STG_DISCOM_1", "month": "2026-11", "billingMonth": "2026-11", "fppaChargePercent": 4.5},
            "update": {"fppaChargePercent": 4.75}
        }
    ]

    for spec in crud_specs:
        rtype = spec["type"]
        # POST Create
        code, body, lat = request("POST", f"/api/resource-center/{rtype}", spec["create"], headers=auth_header)
        created_id = None
        if code == 201 and isinstance(body, dict) and body.get("success") and "data" in body:
            created_data = body["data"]
            created_id = created_data.get("id")
            detail = f"Created ID: {created_id}"
            if "verify_json" in spec:
                k = spec["verify_json"]
                if k in created_data:
                    detail += f", verified JSON key '{k}'={created_data[k]}"
                else:
                    record(f"CRUD: {rtype} [CREATE]", "FAIL", code, lat, f"Missing expected JSON key '{k}'")
                    continue
            record(f"CRUD: {rtype} [CREATE]", "PASS", code, lat, detail)
        else:
            record(f"CRUD: {rtype} [CREATE]", "FAIL", code, lat, str(body))
            continue

        # GET List & verify
        code, body, lat = request("GET", f"/api/resource-center/{rtype}", headers=auth_header)
        if code == 200 and isinstance(body, dict) and isinstance(body.get("data"), list):
            found = any(item.get("id") == created_id for item in body["data"])
            if found:
                record(f"CRUD: {rtype} [READ]", "PASS", code, lat, f"Found ID {created_id} in {len(body['data'])} records")
            else:
                record(f"CRUD: {rtype} [READ]", "FAIL", code, lat, f"ID {created_id} not in list")
        else:
            record(f"CRUD: {rtype} [READ]", "FAIL", code, lat, str(body))

        # PUT Update
        code, body, lat = request("PUT", f"/api/resource-center/{rtype}/{created_id}", spec["update"], headers=auth_header)
        if code == 200 and isinstance(body, dict) and body.get("success"):
            record(f"CRUD: {rtype} [UPDATE]", "PASS", code, lat, f"Updated ID {created_id}")
        else:
            record(f"CRUD: {rtype} [UPDATE]", "FAIL", code, lat, str(body))

        # DELETE
        code, body, lat = request("DELETE", f"/api/resource-center/{rtype}/{created_id}", headers=auth_header)
        if code == 200 and isinstance(body, dict) and body.get("success"):
            record(f"CRUD: {rtype} [DELETE]", "PASS", code, lat, f"Deleted ID {created_id}")
        else:
            record(f"CRUD: {rtype} [DELETE]", "FAIL", code, lat, str(body))

    # 4. Duplicate Insertion Conflict (409)
    # Create discom with unique code STG_DISCOM_DUP
    dup_payload = {"code": "STG_DISCOM_DUP", "legalName": "Duplicate Discom", "stateCode": "STG", "discomType": "Private"}
    code1, body1, lat1 = request("POST", "/api/resource-center/discom-list", dup_payload, headers=auth_header)
    dup_id = body1.get("data", {}).get("id") if isinstance(body1, dict) else None

    # Second creation with identical unique code
    code2, body2, lat2 = request("POST", "/api/resource-center/discom-list", dup_payload, headers=auth_header)
    if code2 == 409:
        record("Conflict: Duplicate Unique Code (409)", "PASS", code2, lat2, "Correctly returned HTTP 409 Conflict")
    else:
        record("Conflict: Duplicate Unique Code (409)", "FAIL", code2, lat2, f"Expected 409, got {code2}: {body2}")

    # Clean up dup_id
    if dup_id:
        request("DELETE", f"/api/resource-center/discom-list/{dup_id}", headers=auth_header)

    # 5. Bulk Upsert & Atomic Rollback
    # 5a. Valid bulk upload
    bulk_valid = [
        {"month": "2026-11", "customerId": "STG_BULK_1", "tradingMargin": 0.03, "tradingMarginGst": 18.0, "proltMargin": 0.01, "proltMarginGst": 18.0},
        {"month": "2026-11", "customerId": "STG_BULK_2", "tradingMargin": 0.04, "tradingMarginGst": 18.0, "proltMargin": 0.02, "proltMarginGst": 18.0}
    ]
    code, body, lat = request("POST", "/api/resource-center/prolt-margin/bulk", bulk_valid, headers=auth_header)
    bulk_ids = []
    if code == 201 and isinstance(body, dict) and body.get("success"):
        bulk_ids = [r["id"] for r in body.get("data", []) if "id" in r]
        record("Bulk: Multi-Row Insert", "PASS", code, lat, f"Inserted {len(bulk_ids)} records")
    else:
        record("Bulk: Multi-Row Insert", "FAIL", code, lat, str(body))

    # 5b. Atomic Rollback Test: 1 valid row + 1 invalid row (invalid month format)
    bulk_invalid = [
        {"month": "2026-11", "customerId": "STG_ROLLBACK_TEST", "tradingMargin": 0.05, "tradingMarginGst": 18.0, "proltMargin": 0.03, "proltMarginGst": 18.0},
        {"month": "INVALID_MONTH_FORMAT", "customerId": "STG_FAIL", "tradingMargin": 0.05, "tradingMarginGst": 18.0, "proltMargin": 0.03, "proltMarginGst": 18.0}
    ]
    code, body, lat = request("POST", "/api/resource-center/prolt-margin/bulk", bulk_invalid, headers=auth_header)
    if code == 400:
        # Verify rollback: check if STG_ROLLBACK_TEST was NOT committed
        code_chk, body_chk, _ = request("GET", "/api/resource-center/prolt-margin", headers=auth_header)
        committed = any(item.get("customerId") == "STG_ROLLBACK_TEST" for item in body_chk.get("data", []))
        if not committed:
            record("Bulk: Atomic Rollback on Invalid Row", "PASS", code, lat, "Transaction rolled back, 0 records committed")
        else:
            record("Bulk: Atomic Rollback on Invalid Row", "FAIL", code, lat, "Partial commit detected! Atomicity violated.")
    else:
        record("Bulk: Atomic Rollback on Invalid Row", "FAIL", code, lat, f"Expected 400, got {code}: {body}")

    # Clean up bulk_ids
    for bid in bulk_ids:
        request("DELETE", f"/api/resource-center/prolt-margin/{bid}", headers=auth_header)

    # 6. Calculations & Trade Report
    # 6a. Direct Landed Cost Calculation
    calc_req = {"state": "GUJARAT", "consumptionKwh": 100000}
    code, body, lat = request("POST", "/api/savings-calculator/calculate", calc_req, headers=auth_header)
    if code == 200 and isinstance(body, dict) and body.get("success") and "data" in body:
        savings = body["data"].get("totalSavings")
        pct = body["data"].get("percentageSavings")
        record("Calculation: Landed Cost & Savings", "PASS", code, lat, f"Savings={savings}, Pct={pct}%")
    else:
        record("Calculation: Landed Cost & Savings", "FAIL", code, lat, str(body))

    # 6b. Trade Report PDF Upload & Parsing
    pdf_path = "/home/ankit-jha/probus-workspace/IEX-Dashboard/pdfs/RTM_18-08-2026.pdf"
    if os.path.exists(pdf_path):
        boundary = "----WebKitFormBoundaryStagingSmokeTest"
        with open(pdf_path, "rb") as f:
            pdf_bytes = f.read()
        
        body_parts = [
            f"--{boundary}\r\n".encode("utf-8"),
            b'Content-Disposition: form-data; name="files"; filename="RTM_18-08-2026.pdf"\r\n',
            b"Content-Type: application/pdf\r\n\r\n",
            pdf_bytes,
            b"\r\n",
            f"--{boundary}--\r\n".encode("utf-8")
        ]
        multipart_body = b"".join(body_parts)
        multipart_headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}"
        }
        code, body, lat = request("POST", "/api/trader-performance/upload", multipart_body, headers=multipart_headers)
        files_list = body.get("files_status") or body.get("data") or []
        first_file_data = files_list[0].get("data", {}) if (isinstance(files_list, list) and files_list) else {}
        total_mwh = first_file_data.get("total_trade_mwh")
        trade_rows = len(first_file_data.get("trades", []))
        if code == 200 and isinstance(body, dict) and body.get("summary", {}).get("successful_files") == 1 and total_mwh is not None:
            record("Trade Report: 96-Block PDF Parsing", "PASS", code, lat, f"1/1 files parsed, total_trade_mwh={total_mwh} MWh, rows={trade_rows}")
        else:
            record("Trade Report: 96-Block PDF Parsing", "FAIL", code, lat, str(body)[:100])
    else:
        record("Trade Report: 96-Block PDF Parsing", "SKIP", 0, 0, f"File not found: {pdf_path}")

    # 7. Scraper Fail-Closed Behavior
    code, body, lat = request("POST", "/api/scraper/sync", {"market": "REC"}, headers=auth_header)
    if code == 500 and isinstance(body, dict) and "REC persistence not yet ported" in str(body):
        record("Scraper: Fail-Closed on Unimplemented (REC)", "PASS", code, lat, "HTTP 500 with descriptive error message")
    else:
        record("Scraper: Fail-Closed on Unimplemented (REC)", "FAIL", code, lat, f"Expected 500 with unported error, got {code}: {body}")

    # 8. Security Hardening
    # 8a. /actuator/env
    code, body, lat = request("GET", "/actuator/env")
    if code in (401, 403, 404):
        record("Security: /actuator/env Protected/Disabled", "PASS", code, lat, f"HTTP {code}")
    else:
        record("Security: /actuator/env Protected/Disabled", "FAIL", code, lat, f"Exposed with {code}!")

    # 8b. Swagger UI
    for swagger_path in ("/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs"):
        code, body, lat = request("GET", swagger_path)
        if code == 404:
            record(f"Security: {swagger_path} Disabled", "PASS", code, lat, "HTTP 404 Not Found")
        else:
            record(f"Security: {swagger_path} Disabled", "FAIL", code, lat, f"Exposed with {code}!")

    print("=" * 80)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] == "FAIL")
    print(f"SUMMARY: Total={len(results)}, Passed={passed}, Failed={failed}")
    print("=" * 80)

    # Save results to JSON file
    out_file = "/home/ankit-jha/.gemini/antigravity/brain/1f6eac15-6d4b-45a4-9365-00da5a2f7b26/scratch/smoke_test_results.json"
    with open(out_file, "w") as f:
        json.dump(results, f, indent=2)
    print(f"Results saved to {out_file}")

    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    run_tests()
