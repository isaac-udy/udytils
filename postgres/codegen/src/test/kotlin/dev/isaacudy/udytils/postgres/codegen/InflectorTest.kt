package dev.isaacudy.udytils.postgres.codegen

import kotlin.test.Test
import kotlin.test.assertEquals

class InflectorTest {
    private val mapper = NameMapper()
    private fun row(t: String) = mapper.rowTypeName(t)
    private fun table(t: String) = mapper.tableTypeName(t)

    @Test
    fun reproducesArcaneRowNames() {
        // These are the contract for ~30 consumer import sites — must not drift.
        assertEquals("CampaignRow", row("campaigns"))
        assertEquals("EntityRow", row("entities"))
        assertEquals("ChatMessageRow", row("chat_messages"))
        assertEquals("SessionRow", row("sessions"))
        assertEquals("EventRow", row("events"))
        assertEquals("SessionChapterRow", row("session_chapters"))
        assertEquals("TranscriptionSegmentRow", row("transcription_segments"))
        assertEquals("UserProfileRow", row("user_profiles"))
        assertEquals("AuditLogRow", row("audit_log"))
        assertEquals("CampaignMemberRow", row("campaign_members"))
        assertEquals("EntityRelationshipRow", row("entity_relationships"))
        assertEquals("RefreshTokenRow", row("refresh_tokens"))
        assertEquals("MigrationJobRow", row("migration_jobs"))
        assertEquals("EventMentionRow", row("event_mentions"))
    }

    @Test
    fun tableTypeNames() {
        assertEquals("CampaignsTable", table("campaigns"))
        assertEquals("AuditLogTable", table("audit_log"))
        assertEquals("ChatMessagesTable", table("chat_messages"))
    }

    @Test
    fun handlesTrickyPluralsWithoutMangling() {
        assertEquals("status", singularize("statuses"))
        assertEquals("box", singularize("boxes"))
        assertEquals("match", singularize("matches"))
        assertEquals("wish", singularize("wishes"))
        assertEquals("message", singularize("messages"))
        assertEquals("image", singularize("images"))
        assertEquals("category", singularize("categories"))
        assertEquals("address", singularize("addresses"))
        // -ves -> -f covers the common case (leaf/wolf/shelf); -fe words like
        // "knife" are ambiguous and rely on rowNameOverrides.
        assertEquals("leaf", singularize("leaves"))
    }

    @Test
    fun handlesIrregularsAndUninflected() {
        assertEquals("person", singularize("people"))
        assertEquals("child", singularize("children"))
        assertEquals("index", singularize("indices"))
        assertEquals("matrix", singularize("matrices"))
        assertEquals("analysis", singularize("analyses"))
        assertEquals("series", singularize("series"))
        assertEquals("species", singularize("species"))
        assertEquals("status", singularize("status"))
        assertEquals("data", singularize("data"))
    }

    @Test
    fun rowNameOverridesWin() {
        val m = NameMapper(rowNameOverrides = mapOf("people" to "Person", "data" to "Datum"))
        assertEquals("PersonRow", m.rowTypeName("people"))
        assertEquals("DatumRow", m.rowTypeName("data"))
    }

    @Test
    fun customSuffixes() {
        val m = NameMapper(tableSuffix = "Entity", rowSuffix = "Record")
        assertEquals("CampaignsEntity", m.tableTypeName("campaigns"))
        assertEquals("CampaignRecord", m.rowTypeName("campaigns"))
    }
}
