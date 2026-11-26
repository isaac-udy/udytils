package dev.isaacudy.udytils.trace

/** Receives the spans of the sites it selects. Both callbacks run synchronously in the traced code. */
interface SpanSink {
    /** The [SiteFlags] this sink requires for [site]; 0 leaves the site untraced for this sink. */
    fun flagsFor(site: TraceSite): Int

    fun onStart(span: Span) {}

    fun onEnd(span: Span) {}
}

/** Stores every span that ends, for tests. */
class InMemorySpanSink(
    private val select: (TraceSite) -> Int = { SiteFlags.RECORD },
) : SpanSink {
    private val ended = mutableListOf<Span>()

    val spans: List<Span> get() = ended.toList()

    override fun flagsFor(site: TraceSite): Int = select(site)

    override fun onEnd(span: Span) {
        ended += span
    }

    fun clear() = ended.clear()
}
