# Script to export latest Swagger/OpenAPI documentation
# Usage: .\scripts\export-api-docs.ps1 [base-url]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutputDir = "src/main/resources/swagger"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Exporting Swagger API Documentation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Ensure output directory exists
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    Write-Host "Created output directory: $OutputDir" -ForegroundColor Green
}

$endpoints = @(
    @{ Name = "all-apis"; File = "api-docs.json"; Description = "All APIs" },
    @{ Name = "authentication"; File = "api-docs-authentication.json"; Description = "Authentication APIs" },
    @{ Name = "admin"; File = "api-docs-admin.json"; Description = "Admin APIs" },
    @{ Name = "webhooks"; File = "api-docs-webhooks.json"; Description = "Webhook APIs" }
)

$successCount = 0
$failCount = 0

foreach ($endpoint in $endpoints) {
    $url = "$BaseUrl/v3/api-docs/$($endpoint.Name)"
    $outputFile = Join-Path $OutputDir $endpoint.File

    Write-Host "Exporting $($endpoint.Description)..." -NoNewline -ForegroundColor Yellow

    try {
        $response = Invoke-WebRequest -Uri $url -Method Get -ErrorAction Stop
        $response.Content | Out-File -FilePath $outputFile -Encoding UTF8
        Write-Host " [OK] Saved to $outputFile" -ForegroundColor Green
        $successCount++
    }
    catch {
        Write-Host " [FAIL] Failed: $_" -ForegroundColor Red
        $failCount++
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Successfully exported: $successCount" -ForegroundColor Green
Write-Host "  Failed: $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Gray" })
Write-Host ""
Write-Host "Files saved to: $OutputDir" -ForegroundColor Cyan
Write-Host ""
Write-Host "Access Swagger UI at: $BaseUrl/swagger-ui.html" -ForegroundColor Yellow
Write-Host ""

