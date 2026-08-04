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
| `dev.isaacudy.udytils:postgres-core` | Runtime: column types, `PostgresMigrator`, `PgNotificationBus`, `PostgresConfig`, `buildHikariDataSource`, `PgNotifyTrigger` | production |
| `dev.isaacudy.udytils:postgres-koin` | Optional Koin module wiring the above | production |
| `dev.isaacudy.udytils:postgres-codegen` | The build-only codegen engine (embedded-PG + Flyway + introspection) | build only |
| `dev.isaacudy.udytils.postgres` (plugin) | Gradle plugin that wires the codegen into your build | build only |
| `dev.isaacudy.udytils:postgres-embedded` | Dev/test helper: `DevServer` + `EmbeddedPostgresLifecycle` start Zonky and hand back a ready `PostgresConfig` | dev/test |

## Apply-and-go

The plugin is published to Maven Central (not the Gradle Plugin Portal), so add
`mavenCentral()` to `pluginManagement.repositories` in `settings.gradle.kts` once:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

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
| `runtimeDependency` | `true` | auto-add `postgres-core` to `implementation` |
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

## Local development database

`postgres-embedded` is the default local-dev story: an **embedded Zonky Postgres**,
running the real Postgres binary in-process. LISTEN/NOTIFY, triggers and column types
behave exactly as they do in production, there is no Docker daemon to keep alive, and it
works unchanged on CI runners.

`DevServer` is the entry point. It starts Postgres, runs Flyway, seeds a brand-new
database with a scenario, and hands back a `PostgresConfig` to feed into your DI wiring:

```kotlin
fun main() {
    // The app owns its own env-var vocabulary; the toolkit reads no environment itself.
    val postgresConfig = when (System.getenv("MYAPP_DEV_DB")) {
        "embedded" -> DevServer.start(
            DevServerConfig(
                storage = DevServerStorage.Persistent(
                    baseDirectory = Path.of(System.getProperty("user.home"), ".myapp", "devdb"),
                ),
                freshScenario = DefaultScenario,
                requestedScenario = System.getenv("MYAPP_DEV_SCENARIO")?.let(Scenarios::byName),
            ),
        ).postgresConfig

        else -> PostgresConfig(jdbcUrl = env("DB_URL"), username = env("DB_USER"), password = env("DB_PASSWORD"))
    }
    startServer(postgresConfig)   // e.g. postgresDependencies(postgresConfig)
}
```

**Storage modes** (`DevServerStorage`):

- **`Persistent(baseDirectory, port = 15432)`** — the recommended app default. Data
  survives restarts, so a hot reload or a `Ctrl-C` doesn't cost you the state you clicked
  your way into, and the fixed port keeps `psql`, IDE data sources and `.env` files
  working. The cluster lives in **`<baseDirectory>/pg<major>`**, where `<major>` is the
  Postgres major version of the bundled Zonky binaries (read from the binaries artifact on
  the classpath, not from a constant). A cluster belongs to the major version that created
  it, so a binaries bump starts a fresh directory beside the old one instead of failing on
  a data directory the new server can't read.
- **`Ephemeral`** — clean temp directory, random port, discarded on shutdown. For demos,
  experiments and tests.

**Seed-once semantics.** A `DevScenario` is a named starting state, written in Kotlin
against your generated Exposed tables; the toolkit ships only the interface and
`EmptyScenario`. Scenarios assume an empty schema, so they run only when the cluster was
just created (`freshlyInitialized`). The two config inputs mean different things:

| Situation | `freshScenario` (your default) | `requestedScenario` (operator asked for it) |
|---|---|---|
| New cluster | applied | applied (wins over `freshScenario`) |
| Existing data | skipped, quietly | **fails loudly** |

Applying a scenario over an existing database would insert on top of whatever is there, so
it isn't supported — wipe the data directory (a `wipeDevDatabase`-style task in your build,
or just delete it) and start again. The error says exactly that, and names the directory.

`DevServer.start` is a **JVM-scoped singleton**: repeat calls return the same handle and
shutdown is a `Runtime.addShutdownHook`, deliberately not tied to any Ktor/application
lifecycle — otherwise auto-reload would kill the database on every classpath change. Each
start logs a banner:

```
────────────────────────────────────────────────────────
 Dev database: embedded-persistent
 Postgres 18.4 on port 15432
 Data directory: /Users/me/.myapp/devdb/pg18
   survives restarts; delete it (or run your project's wipe task) to reset
 Migrations: 1 applied of 1 found
 Seeding: applied scenario 'default' — Admin user and one campaign.
────────────────────────────────────────────────────────
```

For tests, or for anything that wants the Postgres process without the migrate/seed
sequence, use `EmbeddedPostgresLifecycle` directly — it takes the same `dataDirectory` /
`port` options and exposes `freshlyInitialized`.

## Fat jars & ServiceLoader clobbering

`flyway-core` and `flyway-database-postgresql` both ship
`META-INF/services/org.flywaydb.core.extensibility.Plugin` — **the same path, with
disjoint contents**. On a normal classpath (Gradle `run`, tests) both files coexist in
their own jars and Flyway sees the union. In a fat jar built by Shadow (which is what
Ktor's `buildFatJar` uses) the duplicate paths collide and one file silently clobbers the
other. Flyway then fails in one of two ways:

- a `NullPointerException` from `PluginRegister` while the Flyway *configuration* is being
  built — before any connection, so the server dies at boot; or
- worse, Flyway boots, finds **zero migrations**, and reports success — leaving an empty
  schema in production while every local run works.

`PostgresMigrator.migrate()` checks the union of every copy of that file on the classpath
before configuring Flyway, and fails fast with a diagnostic naming this problem when
either artifact's entries are missing. (Package-prefix check, so it survives Flyway
upgrades. Pass `verifyFlywayPlugins = false` to opt out.)

Fixing it in your build:

1. Shadow's documented answer is `mergeServiceFiles()` — **do not take it on faith**. It
   was confirmed *not* merging under Shadow 9.1.0 in two independent repos. Apply it,
   build, then look inside the jar:
   ```sh
   unzip -p app/server/build/libs/*-all.jar META-INF/services/org.flywaydb.core.extensibility.Plugin
   ```
   If both artifacts' entries are there, that's the whole fix.
2. Otherwise, check a **hand-merged copy** into the executable module's own resources
   (`src/main/resources/META-INF/services/org.flywaydb.core.extensibility.Plugin`) —
   project resources land last in the fat jar and win the conflict:
   ```sh
   V=12.9.0   # the version that actually resolves, per `./gradlew :app:server:dependencies`
   F=~/.gradle/caches/modules-2/files-2.1/org.flywaydb
   {
     unzip -p "$F"/flyway-core/$V/*/flyway-core-$V.jar META-INF/services/org.flywaydb.core.extensibility.Plugin
     echo
     unzip -p "$F"/flyway-database-postgresql/$V/*/flyway-database-postgresql-$V.jar META-INF/services/org.flywaydb.core.extensibility.Plugin
   } | grep -v '^$' | sort -u > org.flywaydb.core.extensibility.Plugin
   ```
   **Regenerate whenever the resolved Flyway version changes** (usually this toolkit's
   pin, which wins conflict resolution over your own catalog). A stale copy fails at boot
   with `ServiceConfigurationError: Provider <class> not found`.

Only running the jar exercises this: `run`, unit tests and migration tests against
embedded Postgres all pass while the jar is broken. A CI step that builds the fat jar,
boots it against an embedded dev database and asserts the migration count in the log is
the gate that catches both variants.

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
