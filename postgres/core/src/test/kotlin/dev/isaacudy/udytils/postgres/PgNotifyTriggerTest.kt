package dev.isaacudy.udytils.postgres

import kotlin.test.Test
import kotlin.test.assertTrue

class PgNotifyTriggerTest {

    @Test
    fun emitsIdempotentFunctionAndTrigger() {
        val ddl = PgNotifyTrigger.ddl(table = "entities", channel = "entities", payloadColumn = "campaign_path")
        assertTrue("CREATE OR REPLACE FUNCTION notify_entities_entities()" in ddl, ddl)
        assertTrue("PERFORM pg_notify('entities', COALESCE(NEW.campaign_path, OLD.campaign_path));" in ddl, ddl)
        assertTrue("RETURN COALESCE(NEW, OLD);" in ddl, ddl)
        assertTrue("DROP TRIGGER IF EXISTS entities_entities_notify ON entities;" in ddl, ddl)
        assertTrue("AFTER INSERT OR UPDATE OR DELETE ON entities" in ddl, ddl)
        assertTrue("\$\$ LANGUAGE plpgsql;" in ddl, ddl)
    }

    @Test
    fun appliesCastForNonTextKeys() {
        val ddl = PgNotifyTrigger.ddl("campaigns", "campaigns", "id", cast = "text")
        assertTrue("COALESCE(NEW.id, OLD.id)::text" in ddl, ddl)
    }

    @Test
    fun scriptConcatenatesMultiple() {
        val script = PgNotifyTrigger.script(
            PgNotifyTrigger.Trigger("sessions", "sessions_list", "campaign_path"),
            PgNotifyTrigger.Trigger("sessions", "sessions_detail", "id", cast = "text"),
        )
        assertTrue("notify_sessions_sessions_list()" in script, script)
        assertTrue("notify_sessions_sessions_detail()" in script, script)
    }
}
