# web

Server-rendered HTML with [htmx](https://htmx.org), built on kotlinx.html and Ktor. JVM only.

| Artifact | Contents |
|---|---|
| `dev.isaacudy.udytils:htmx` | Typed `hx-*`, `sse-*` and Alpine CSP attributes; `ElementId`; Ktor request/response helpers; fragment rendering; `OobList`; bundled htmx, htmx-ext-sse and Alpine CSP builds |
| `dev.isaacudy.udytils:html-snapshot` | `HtmlSnapshots` golden-file assertions over jsoup-normalised HTML |

## Markup

```kotlin
val greetings = ElementId("greetings")

fun FlowContent.greetingForm() {
    form {
        hx {
            post("/greetings")
            target(greetings)
            swap(SwapStyle.BeforeEnd)
        }
        input(name = "text")
        button { +"Add" }
    }
}
```

Attribute values that htmx or Alpine would evaluate as JavaScript cannot be expressed: there is no
`hx-on`, `Trigger` rejects event filters, and `alpine { }` accepts only names of members of
components registered with `Alpine.data(...)` in a script file. The page can therefore run under a
`script-src 'self'` content security policy.

## Page head

```kotlin
head {
    htmxConfig()                          // no eval, no inline indicator styles
    htmxScripts()                         // htmx + the sse extension, deferred
    script(src = "/static/app.js") { defer = true }  // registers Alpine components on alpine:init
    alpineScript()                        // must come after the scripts that register components
}
```

`Route.htmxAssets()` serves the bundled scripts under `/assets/htmx` with an immutable cache
policy; their file names carry the version.

## Requests and responses

`call.isHtmx` tells a swap request from a page load. `call.respondHtmlFragment { }` answers with
elements and no document; `varyOnHtmx()` marks a response whose body depends on the difference.
`hxRedirect`, `hxLocation`, `hxTrigger`, `hxRetarget`, `hxReswap`, `hxPushUrl` and `hxRefresh` set
the matching `HX-*` response headers.

## Live lists

`OobList` turns successive versions of a list into out-of-band swaps: deleted items, re-rendered
items, and appended items, each in its own `<template>` so table rows parse. A reorder or an insert
before an existing item falls back to replacing the container's children.

```kotlin
val rows = OobList.tableBody(ElementId("greetings"), key = Greeting::id) { greeting -> greetingRow(greeting) }

sse("/greetings/events") {
    flowOfGreetings().oobUpdates(rows).collect { send(ServerSentEvent(data = it, event = "greetings")) }
}
```

On the page, a sink element receives the events without swapping itself:

```kotlin
div {
    sse { connect("/greetings/events"); swap("greetings") }
    hx { swap(SwapStyle.None) }
}
```

## Snapshots

```kotlin
private val snapshots = HtmlSnapshots()

@Test
fun `empty page`() {
    snapshots.assertMatches("GreetingsPage/empty", renderedHtml)
}
```

Goldens live in `src/test/snapshots/html/` by default. Run the tests with
`-Dudytils.htmlSnapshot.record=true` to write them; declare the directory as a test input so Gradle
reruns tests when a golden changes.
