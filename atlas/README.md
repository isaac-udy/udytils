# Atlas

A UI atlas generator for Enro + Paparazzi projects. It scans a repository for
`@NavigationDestination` nodes, `.open()` edges, and Paparazzi snapshot goldens, then
produces an interactive HTML atlas (pan/zoom, search, annotations, variant selector) and
a machine-readable `manifest.json`.

## Modules

| Artifact | What it is |
|---|---|
| `atlas-core` | The scanner, assembler, and HTML renderer as a plain JVM library |
| `atlas-gradle-plugin` | Gradle plugin (`dev.isaacudy.udytils.atlas`) wrapping the core library |

## Gradle plugin

Apply at the repository root:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts (root)
plugins {
    id("dev.isaacudy.udytils.atlas") version "<version>"
}

atlas {
    // Chrome edges are manually declared edges (e.g. shell navigation) that the
    // scanner cannot discover from .open() calls.
    chromeEdge("HomeDestination", "SettingsDestination")
    chromeEdge("HomeDestination", "ProfileDestination")

    // Feature-group fallbacks map repo-relative path prefixes to group labels,
    // checked in order, for files whose package does not contain a `feature` segment.
    featureGroupFallback("app/admin", "admin")
    featureGroupFallback("app/client", "app")

    // Optional overrides — auto-discovered by default.
    // projectName.set("my-project")
    // outputDirectory.set(layout.buildDirectory.dir("ui-atlas"))
}
```

Then run:

```
./gradlew generateUiAtlas
```

Output lands in `build/ui-atlas/` (configurable) and contains:
- `index.html` — self-contained interactive atlas
- `manifest.json` — machine-readable manifest
- `images/` — copied golden PNGs

## Core library

The core library can be used standalone without Gradle:

```kotlin
val summary = generateAtlas(
    AtlasConfig(
        repoRoot = File("/path/to/repo"),
        outputDir = File("/path/to/output"),
        chromeEdges = listOf(ChromeEdge("HomeDestination", "SettingsDestination")),
    )
)
println("${summary.nodes} nodes, ${summary.edges} edges")
```

## `manifest.json` schema

The manifest is a first-class contract. Agents and tooling may consume it; changes
must be additive.

```
AtlasManifest {
  projectName: String        — the project name (from settings.gradle or config)
  generatedAt: String        — ISO 8601 timestamp
  nodes: [AtlasNode]         — all destination nodes (real + synthetic)
  edges: [AtlasEdge]         — resolved navigation edges
  unresolvedEdges: [UnresolvedEdge] — edges that could not be resolved to a node
  unmatchedGoldens: Int      — golden files that matched no destination
  totalGoldens: Int          — total golden PNG files discovered
}

AtlasNode {
  qualifiedName: String      — fully qualified destination name (package.DestName)
  destinationName: String    — simple destination class name
  screenName: String         — destination name without "Destination" suffix
  displayName: String        — human-readable name (disambiguated if duplicates exist)
  featureGroup: String       — feature grouping label
  moduleLabel: String        — Gradle module path derived from file location
  sourceFile: String         — repo-relative path to the declaring source file
  packageName: String        — Kotlin package name
  isWithResult: Boolean      — true if the destination key extends NavigationKey.WithResult
  navigationPath: String?    — value from @NavigationPath annotation, if present
  isShellActive: Boolean     — true if the screen uses shellActive() or shellEmpty()
  variants: [AtlasVariant]   — matched snapshot variants
  defaultVariantIndex: Int   — index of the default variant to display
  synthetic: Boolean         — true if this node was synthesized from orphan goldens
}

AtlasVariant {
  label: String              — variant label (e.g. "Default", "Loaded", "Error")
  imagePath: String          — repo-relative path to the golden PNG
  width: Int                 — image width in pixels (from PNG header)
  height: Int                — image height in pixels (from PNG header)
}

AtlasEdge {
  source: String             — qualifiedName of the source node
  target: String             — qualifiedName of the target node
  kind: EdgeKind             — OPEN | RESULT | CHROME
}

UnresolvedEdge {
  file: String               — repo-relative source file path
  line: Int                  — line number
  text: String               — the source line text
}
```
