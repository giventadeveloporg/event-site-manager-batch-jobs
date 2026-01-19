#!/bin/bash
# Script to export latest Swagger/OpenAPI documentation
# Usage: ./scripts/export-api-docs.sh [base-url]

BASE_URL="${1:-http://localhost:8080}"
OUTPUT_DIR="src/main/resources/swagger"

echo "========================================"
echo "  Exporting Swagger API Documentation"
echo "========================================"
echo ""

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Export all API groups
declare -a endpoints=(
    "all-apis:api-docs.json:All APIs"
    "authentication:api-docs-authentication.json:Authentication APIs"
    "admin:api-docs-admin.json:Admin APIs"
    "webhooks:api-docs-webhooks.json:Webhook APIs"
)

success_count=0
fail_count=0

for endpoint_info in "${endpoints[@]}"; do
    IFS=':' read -r name file desc <<< "$endpoint_info"
    url="$BASE_URL/v3/api-docs/$name"
    output_file="$OUTPUT_DIR/$file"

    echo -n "Exporting $desc... "

    if curl -s -f "$url" -o "$output_file"; then
        echo "✓ Saved to $output_file"
        ((success_count++))
    else
        echo "✗ Failed to download from $url"
        ((fail_count++))
    fi
done

echo ""
echo "========================================"
echo "  Summary"
echo "========================================"
echo "  Successfully exported: $success_count"
echo "  Failed: $fail_count"
echo ""
echo "Files saved to: $OUTPUT_DIR"
echo ""
echo "Access Swagger UI at: $BASE_URL/swagger-ui.html"
echo ""


