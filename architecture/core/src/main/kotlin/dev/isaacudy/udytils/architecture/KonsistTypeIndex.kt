package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.ext.koscope.declarationsOf

/**
 * Makes Konsist resolve parent and child types against [scope] instead of its own project index.
 *
 * Konsist resolves types through `DataCore`, an index built by `scopeFromProject()`, which walks
 * every file under the project root, `build/` and `node_modules` included, before parsing the
 * Kotlin files. [scope] must contain every source file that a governed declaration's types are
 * declared in, such as the `src` directories of every module and embedded build.
 *
 * Returns false, and leaves Konsist's own index in place, when this Konsist version does not store
 * the index in the fields that this function replaces, or when Konsist has already built the index.
 */
fun useKonsistTypeIndex(scope: KoScope): Boolean {
    val values: Map<String, () -> Any> = mapOf(
        "declarations" to { scope.declarationsOf<KoBaseDeclaration>() },
        "classes" to { scope.classes() },
        "interfaces" to { scope.interfaces() },
        "objects" to { scope.objects() },
        "typeAliases" to { scope.typeAliases },
    )
    return try {
        val lazies = konsistTypeIndexInitializers(values.keys)
        if (lazies.values.any { (lazy, _) -> lazy.isInitialized() }) return false
        for ((name, entry) in lazies) entry.second.set(entry.first, values.getValue(name))
        true
    } catch (error: ReflectiveOperationException) {
        System.err.println("Konsist's type index is not replaceable in this Konsist version (${error.javaClass.simpleName}: ${error.message}); Konsist parses the whole project root.")
        false
    }
}

internal val KONSIST_TYPE_INDEX_PROPERTIES = listOf("declarations", "classes", "interfaces", "objects", "typeAliases")

/**
 * Each `DataCore` property's `Lazy` and the field that stores its initializer. The delegate fields
 * are static final, so the initializer field of each `Lazy` is the replaceable part.
 */
internal fun konsistTypeIndexInitializers(names: Collection<String>): Map<String, Pair<Lazy<*>, java.lang.reflect.Field>> {
    val dataCore = Class.forName("com.lemonappdev.konsist.core.model.DataCore")
    return names.associateWith { name ->
        val lazy = dataCore.getDeclaredField("$name\$delegate").apply { isAccessible = true }.get(null) as Lazy<*>
        lazy to lazy.javaClass.getDeclaredField("initializer").apply { isAccessible = true }
    }
}
