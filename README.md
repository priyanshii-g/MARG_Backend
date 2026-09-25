# MARG v0.5.0 — Kaziranga–Karbi Anglong greenfield alignment prototype

MARG is a preliminary greenfield highway/expressway corridor-planning prototype for the Kaziranga–Karbi Anglong study region in Assam.

This version makes the routing core independent of latitude/longitude degree distances and establishes a typed spatial-data-provider architecture.

## What changed in v0.5

1. **Metric routing grid:** A* now constructs its artificial search grid in projected metres rather than degree units. The current study-area projection is WGS84 / UTM zone 46N (`EPSG:32646`).
2. **Meter-based routing resolution:** the default routing grid is `250 m` rather than `0.002°`.
3. **Provider abstraction:** routing consumes a `LulcProvider` instead of depending directly on the Bhuvan sampler. `BhuvanLulcProvider` is the current implementation.
4. **Provider base contract:** `SpatialProvider<T>` provides the common point-sampling/provenance abstraction that later flood, terrain, settlement, environmental and other providers can build on.
5. **Dataset-independent LULC cost layer:** `LulcCostLayer` no longer has Bhuvan in its class name. The route engine therefore depends on the type of information rather than its source.
6. **Metric distance constraints:** maximum route deviation is enforced against physical route length computed in the routing CRS.

## Study region

Kaziranga–Karbi Anglong, Assam.

Configured research input points:

- A: `26.58970, 93.40035` (Kohora Chariali area)
- B: `25.84573, 93.43781` (Diphu area)

These points are prototype inputs and are not a proposed legal/project alignment.

## Scope boundary

The generated lines are preliminary planning corridors. They are not final highway centerlines, construction-ready geometric designs, environmental-clearance decisions, land-acquisition determinations, or legal displacement assessments.

The current A* graph is an artificial geographic grid. Existing roads are not the routing graph.

## Run with Java 25

From the project root:

```cmd
java -version
mvn -version
mvn clean spring-boot:run
```

The application runs at `http://localhost:8080`.

## Verify the runtime and projection

```text
http://localhost:8080/api/system
```

The response reports the actual Java runtime, the routing model, and the coordinate reference system used internally.

## Route endpoints

### Distance baseline

```text
http://localhost:8080/api/routes?mode=distance
```

A* minimizes physical movement distance on the projected metric grid.

### Bhuvan-LULC-aware route

```text
http://localhost:8080/api/routes?mode=lulc
```

The route cost combines physical movement distance with the configured LULC penalty supplied through `LulcProvider`.

### Compare baseline and LULC-aware route

```text
http://localhost:8080/api/routes?mode=compare
```

### Maximum route deviation

Example:

```text
http://localhost:8080/api/routes?mode=lulc&maxDeviationPercent=15
```

MARG first computes the physical distance-only baseline and derives:

```text
maxAllowedRouteLength = baselineLength × (1 + maxDeviationPercent / 100)
```

The constrained router first returns the ordinary LULC route when it already satisfies the limit. Otherwise it uses a Lagrangian relaxation that adds a distance term to the scalarized A* objective. The constrained stage is an approximate constrained optimization method rather than an exact solver guarantee.

## Routing resolution

Default:

```text
250 metres
```

The grid is no longer expressed in degrees. A grid node is defined in the projected metric CRS and converted to WGS84 only when a spatial provider is queried or when GeoJSON is generated.

For a study region near `93.4°E`, UTM zone 46N is the local metric projection currently configured by `marg.routing.utm-zone`.

Configuration:

```yaml
marg:
  routing:
    resolution-meters: 250
    utm-zone: 46
```

Allowed routing resolution is currently `50–5000 m`.

## Coordinate architecture

```text
WGS84 latitude/longitude
          |
          v
WGS84 / UTM zone 46N
          |
          v
metric X/Y in metres
          |
          v
artificial A* grid
          |
          v
route nodes
          |
          v
WGS84 GeoJSON
```

The routing algorithm therefore uses metre-based physical distances. External GIS services can continue to operate in their native/WGS84-compatible spatial interface.

## LULC provider architecture

```text
                  LulcProvider
                       |
              +--------+--------+
              |                 |
              v                 v
     BhuvanLulcProvider   future/local provider
              |
              v
      BhuvanLulcSampler
              |
              v
        WMS GetFeatureInfo
```

Routing and cost calculation use:

```java
LulcProvider
```

not:

```java
BhuvanLulcSampler
```

This is intentional: Bhuvan is a current **data source**, not the routing contract.

The base interface is:

```java
SpatialProvider<T>
```

and `LulcProvider` extends it with region preloading and dataset metadata.

The current implementation is:

```java
BhuvanLulcProvider implements LulcProvider
```

## Planned provider types

The architecture is intended to support additional typed providers without coupling them to A*:

```text
LulcProvider
FloodProvider
TerrainProvider
SettlementProvider
ProtectedAreaProvider
RoadNetworkProvider
```

Examples of future implementations:

```text
BhuvanLulcProvider
BhuvanFloodProvider
DemTerrainProvider
PariveshProtectedAreaProvider
OsmRoadNetworkProvider
```

Only the LULC provider is implemented in this version. The other providers should be introduced when their actual data model is implemented, rather than as empty placeholder classes.

## LULC acquisition

The current Bhuvan implementation queries the Assam LULC 1:50,000 (2015–16) layer through WMS `GetFeatureInfo` and caches point classifications on a `0.01°` sample grid.

Configuration:

```yaml
marg:
  bhuvan:
    lulc:
      sample-resolution-deg: 0.01
      query-half-size-deg: 0.004
      parallelism: 6
      cache-file: data/cache/bhuvan-lulc-samples.json
      max-remote-samples: 4000
```

The routing grid and the LULC sampling grid are intentionally different resolutions. The provider abstraction isolates this difference.

Delete `data/cache/bhuvan-lulc-samples.json` when a fresh remote acquisition is required.

LULC endpoints:

```text
http://localhost:8080/api/bhuvan/lulc/sample?lat=26.5897&lon=93.40035
http://localhost:8080/api/bhuvan/lulc/check
http://localhost:8080/api/bhuvan/lulc/cache
```

## LULC policy

The runtime policy is in `src/main/resources/application.yml`:

```yaml
marg:
  routing:
    lulc:
      penalties:
        BUILT_UP: 1.5
        FOREST: 0.8
        WETLAND_WATER: 2.0
        AGRICULTURE: 0.25
        GRASS_GRAZING: 0.10
        BARREN_WASTELAND: 0.0
        SNOW: 2.5
        UNKNOWN: 0.0
```

These are MARG model parameters. They are not Bhuvan values, statutory weights, or legal/regulatory thresholds.

Current routing formula:

```text
edge cost = physical distance × (1 + sum of active spatial penalty contributions)
```

A separate hard-constraint mechanism exists through `CostAssessment.blocked()` and can later be used for planning rules that require explicit avoidance.

## LULC cost-surface endpoint

```text
http://localhost:8080/api/routes/cost-surface
```

The endpoint returns the sampled LULC grid as GeoJSON polygons with:

- LULC class
- MARG penalty multiplier
- remote-success flag
- sample latitude/longitude

This is a diagnostic view of the spatial field used by the current LULC layer.

## Existing Bhuvan map proxy

For visualization only:

```text
http://localhost:8080/api/bhuvan/map/assam-lulc-50k-1516?minLon=93.10&minLat=25.75&maxLon=93.75&maxLat=26.65
```

The routing engine does not infer LULC from rendered map colors.

## Cost and route architecture

```text
                     A + B
                       |
                       v
               A-B search window
                       |
                       v
              projected metric grid
                       |
          +------------+-------------+
          |                          |
          v                          v
     LulcProvider               future providers
          |                     flood / terrain /
          v                     settlement / PA /
    LULC CostLayer             roads / rail etc.
          |                          |
          +------------+-------------+
                       |
                       v
                CompositeCostSurface
                       |
                       v
                       A*
                       |
                       v
                candidate alignment
                       |
                       v
          metrics / GeoJSON / map UI
```

The key software separation is:

```text
A* → CostSurface → CostLayer → Provider → External data source
```

The A* implementation therefore does not know whether a cost came from Bhuvan, a downloaded GeoTIFF, PARIVESH, OSM or another source.

## Constrained search

The route-length constraint is a physical-distance constraint:

```text
L(route) <= Lmax
```

where distance is computed in the projected routing CRS.

The algorithm uses a feasibility-first strategy:

```text
ordinary LULC A*
        |
        | route fits budget?
       / \
     yes  no
      |    |
   return  Lagrangian relaxation
           + A* with lambda × distance
           |
           v
      feasible candidate
```

The response reports the requested maximum deviation and whether the resulting route satisfies it.

## Frontend

The `frontend/index.html` page is a small Leaflet diagnostic viewer. It can show:

- distance baseline
- LULC-aware route
- LULC route with a 15% maximum deviation
- LULC cost surface

Run a simple local server from `frontend` if necessary:

```cmd
python -m http.server 5500
```

Then open:

```text
http://localhost:5500
```

## Testing

The routing core has unit tests for:

- baseline path generation
- UTM forward/inverse round-trip
- constrained physical-length behavior
- the fast path when an unconstrained route already fits the length budget

The core projection/router classes can also be compiled independently from Spring for algorithmic smoke testing.

## Next implementation stage

The next provider should be the **flood-hazard provider**.

It should be introduced as a separate provider/cost layer and tested independently before being composed with LULC:

```text
FloodProvider
     |
     v
FloodCostLayer
     |
     +----> CompositeCostSurface
```

After flood, the next layers can be added one at a time:

```text
LULC
  ↓
Flood
  ↓
Protected areas / forest / ESZ
  ↓
Settlements / population exposure
  ↓
Terrain / slope
  ↓
Existing roads / railways
  ↓
Multi-objective alternatives
```
