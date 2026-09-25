#!/usr/bin/env bash
set -euo pipefail

# Example direct Bhuvan WMS request for the configured Assam LULC 50K (2015-16) layer.
# Requires curl and an internet connection. The server-side MARG endpoint performs the same request.

BASE='https://bhuvan-vec2.nrsc.gov.in/bhuvan/wms'
LAYER='lulc:AS_LULC50K_1516'
BBOX='93.10,25.75,93.75,26.65'
OUT="bhuvan_assam_lulc_50k_1516.png"

curl -L --fail --get "$BASE" \
  --data-urlencode "service=WMS" \
  --data-urlencode "request=GetMap" \
  --data-urlencode "version=1.1.1" \
  --data-urlencode "layers=$LAYER" \
  --data-urlencode "styles=" \
  --data-urlencode "srs=EPSG:4326" \
  --data-urlencode "bbox=$BBOX" \
  --data-urlencode "width=1600" \
  --data-urlencode "height=1200" \
  --data-urlencode "format=image/png" \
  --data-urlencode "transparent=true" \
  -o "$OUT"

echo "Saved $OUT"
