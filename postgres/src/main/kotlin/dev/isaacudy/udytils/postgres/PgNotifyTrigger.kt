package dev.isaacudy.udytils.postgres

/**
 * Produce-side companion to [PgNotificationBus]: builds the idempotent
 * `LISTEN/NOTIFY` trigger DDL that fires `pg_notify(channel, payload)` on every
 * row change of a table.
 *
 * The bus is only half the story — every project that wants change
 * notifications has to hand-write the same `CREATE FUNCTION` + `CREATE TRIGGER`
 * boilerplate, and re-discover the subtle bits (the `COALESCE(NEW, OLD)`
 * return, the `::text` cast for non-text keys, the DROP-IF-EXISTS / CREATE OR
 * REPLACE idempotency). This captures it once.
 *
 * ## Where to put the output
 *
 * Trigger/function bodies are *code*, not schema, so a versioned `V__` Flyway
 * migration is the wrong home: once a `V__` migration has run on a database it
 * never runs again, so editing the trigger later silently has no effect.
 *
 * Put the generated DDL in a Flyway **repeatable** migration —
 * `R__notify_triggers.sql` — which re-runs whenever its checksum changes. The
 * DROP-IF-EXISTS / CREATE-OR-REPLACE pattern below makes a fresh DB and an
 * existing one converge to the same trigger state. Generate the file content
 * from a small build step, or paste the output in.
 */
object PgNotifyTrigger {

    /**
     * DDL for one trigger that notifies [channel] with the value of
     * [payloadColumn] (coalescing NEW/OLD so DELETE works). [cast] is appended
     * to the payload expression for non-text keys, e.g. `cast = "text"` emits
     * `COALESCE(NEW.id, OLD.id)::text`.
     */
    fun ddl(
        table: String,
        channel: String,
        payloadColumn: String,
        cast: String? = null,
    ): String {
        val fn = "notify_${table}_${channel}"
        val trigger = "${table}_${channel}_notify"
        val castSuffix = cast?.let { "::$it" } ?: ""
        return """
            |CREATE OR REPLACE FUNCTION $fn() RETURNS trigger AS ${'$'}${'$'}
            |BEGIN
            |    PERFORM pg_notify('$channel', COALESCE(NEW.$payloadColumn, OLD.$payloadColumn)$castSuffix);
            |    RETURN COALESCE(NEW, OLD);
            |END;
            |${'$'}${'$'} LANGUAGE plpgsql;
            |
            |DROP TRIGGER IF EXISTS $trigger ON $table;
            |CREATE TRIGGER $trigger
            |AFTER INSERT OR UPDATE OR DELETE ON $table
            |FOR EACH ROW EXECUTE FUNCTION $fn();
        """.trimMargin()
    }

    /** Convenience: concatenate [ddl] for several triggers into one script body. */
    fun script(vararg triggers: Trigger): String =
        triggers.joinToString("\n\n") { ddl(it.table, it.channel, it.payloadColumn, it.cast) }

    data class Trigger(
        val table: String,
        val channel: String,
        val payloadColumn: String,
        val cast: String? = null,
    )
}
