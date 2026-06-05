# Udytils Postgres

A small toolkit for **Postgres + Exposed** on the JVM:

- **Flyway migrations** are your single source of truth.
- A build step applies them to an **embedded Postgres**, snapshots the schema to
  a committed, diffable `schema.sql`, and **generates Exposed `Table`/`Row`
  sources** from the live schema.
- A thin **runtime library** ships the custom column types, a Flyway migrator, a
  `LISTEN/NOTIFY` → Flow bus, connection config, and (optionally) Koin wiring.

## Artifacts

| Coordinates | What it is | Scope |
|---|---|---|
| `dev.isaacudy.udytils:postgres` | Runtime: column types, `PostgresMigrator`, `PgNotificationBus`, `PostgresConfig`, `buildHikariDataSource`, `PgNotifyTrigger` | production |
| `dev.isaacudy.udytils:postgres-koin` | Optional Koin module wiring the above | production |
| `dev.isaacudy.udytils:postgres-codegen` | The build-only codegen engine (embedded-PG + Flyway + introspection) | build only |
| `dev.isaacudy.udytils.postgres` (plugin) | Gradle plugin that wires the codegen into your build | build only |
| `dev.isaacudy.udytils:postgres-embedded` | Dev/test helper: start Zonky, get a `PostgresConfig` | dev/test |

## Apply-and-go

```kotlin
// build.gradle.kts of a kotlin("jvm") (server) module
plugins {
    kotlin("jvm")
    id("dev.isaacudy.udytils.postgres") version "<udytilsVersion>"
}

postgresCodegen {
    outputPackage.set("app.example.db.tables") // REQUIRED
}
```

1. Drop your Flyway migrations in `src/main/resources/db/migration/V1__*.sql`.
2. Run a build. The plugin registers two tasks (stable public API):
   - **`exportPostgresSchema`** → writes/refreshes `schema.sql` (commit it).
   - **`generatePostgresTables`** → emits one Exposed `Table`/`Row` file per
     table under `build/generated/...`, added to your main source set.
   Both are hooked into `compileKotlin`, so a plain `build` keeps them current.
3. Use the generated tables + the runtime helpers:

```kotlin
transaction {
    WidgetsTable.insert { it.setFromRow(WidgetRow(...)) }
    WidgetsTable.selectAll().map(::WidgetRow)
}
```

## Configuration (`postgresCodegen { }`)

| Field | Default | Notes |
|---|---|---|
| `outputPackage` | — (**required**) | package for generated files |
| `migrationsDir` | `src/main/resources/db/migration` | Flyway dir |
| `schemaSnapshotFile` | `<project>/schema.sql` | committed snapshot, owned by you |
| `columnTypesPackage` | `dev.isaacudy.udytils.postgres` | where generated code imports column types from |
| `schemaName` | `public` | introspected schema |
| `excludedTables` | `{flyway_schema_history}` | skipped in gen + snapshot |
| `tableSuffix` / `rowSuffix` | `Table` / `Row` | generated type suffixes |
| `runtimeDependency` | `true` | auto-add `:postgres` to `implementation` |
| `engineVersion` | plugin version | codegen-engine/runtime version to resolve |
| `zonkyBinaries` | all 4 (darwin/linux × arm/amd) | trim to your dev + CI arch if desired |

Escape hatches:

```kotlin
postgresCodegen {
    rowNameOverride("people", "Person")          // irregular plurals
    sqlTypeOverride("citext", "String", "text(\"{name}\")")
    sqlTypeOverride("numeric", "BigDecimal", "decimal(\"{name}\", {precision}, {scale})", listOf("java.math.BigDecimal"))
}
```

## Supported SQL types

`text`, `varchar(n)`, `char(n)`, `integer`, `bigint`, `smallint`, `boolean`,
`real`, `double precision`, `numeric(p,s)`, `uuid` (with `.autoGenerate()` for
`gen_random_uuid()` / `uuid_generate_v4()` defaults), `timestamptz`,
`timestamp`, `date`, `time`, `jsonb`, `json`, `text[]`, `bytea`. Anything else
fails the build with a message pointing at `sqlTypeOverride`.

## LISTEN/NOTIFY

Consume with `PgNotificationBus.listen(channel)`. Produce the matching triggers
with `PgNotifyTrigger` — put the generated DDL in a Flyway **repeatable**
migration (`R__notify_triggers.sql`), because trigger bodies are code, not
schema, and a versioned `V__` migration would never re-run after an edit:

```kotlin
PgNotifyTrigger.ddl(table = "widgets", channel = "widgets", payloadColumn = "id", cast = "text")
```

## Gotchas

- **`outputPackage` is required** — there is intentionally no default.
- **SLF4J binding**: the runtime logs via `slf4j-api` with no bundled binding.
  Add a binding (e.g. `logback-classic`) or `PgNotificationBus` reconnect
  warnings and migrate logs go to the NOP logger (silent).
- **Zonky needs a non-root user**: `initdb` refuses to run as uid 0, so the
  codegen tasks (and any Docker build that runs them) must run as a non-root
  user.
- **kotlin/JVM only**: the plugin assumes a `compileKotlin` task and a `main`
  source set. Apply it to a library module whose jar other modules depend on, so
  generated symbols are visible across module boundaries.
- **Manual fallback** (no plugin): depend on `postgres-codegen` + the Zonky
  binaries on a build-only configuration and run
  `dev.isaacudy.udytils.postgres.codegen.TableGenMainKt` /
  `SchemaExportMainKt` via `JavaExec`, passing a `.properties` file path.
- **Composite (`includeBuild`) consumers**: a top-level `includeBuild` cannot
  contribute the plugin to `plugins {}`. Apply it via the buildscript classpath
  instead (`buildscript { dependencies { classpath("dev.isaacudy.udytils:postgres-gradle-plugin:<v>") } }`
  then `apply(plugin = "dev.isaacudy.udytils.postgres")`), with the jar resolved
  via your `dependencySubstitution`.
