# Prerequisites: backend on localhost:9096 (.\gradlew.bat bootRun from repo root)
$ErrorActionPreference = "Stop"
$base = "http://localhost:9096"
Write-Host "=== QA Sentinel REST smoke ($base) ==="

$r0 = Invoke-WebRequest -Uri "${base}/api/v1/qa/health" -UseBasicParsing
if ($r0.StatusCode -ne 200) { throw "health failed" }
Write-Host "[OK] GET /api/v1/qa/health"

$report = '{"projectKey":"DEMO","issueKey":"DEMO-101","traceId":"smoke-1","passed":true,"message":"smoke pass","screenshotHint":"qa-runner/test-results/dynamic-failure.png"}'
$r1 = Invoke-WebRequest -Uri "${base}/api/v1/qa/report" -Method Post -Body $report -ContentType "application/json; charset=utf-8" -UseBasicParsing
if ($r1.StatusCode -ne 200) { throw "qa-report expected 200" }
Write-Host "[OK] POST /api/v1/qa/report ->" $r1.StatusCode

Write-Host "Optional: POST /api/v1/qa/run (requires npx playwright in qa-runner/) — not executed in smoke by default."
Write-Host "=== Smoke complete ==="
