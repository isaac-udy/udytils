package dev.isaacudy.udytils.error

import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Compose resources is compileOnly, so it is absent here as it is on a server classpath. Test
 * runners Java-serialize a test's failure; an Error from that serialization hangs Gradle's test
 * worker instead of failing the test.
 */
class PresentableExceptionJavaSerializationTest {

    private class TestFailure : PresentableException(title = "Not enough", message = "Needs more.")

    @Test
    fun `Java serialization fails with an Exception, not an Error, without compose resources`() {
        assertFailsWith<NotSerializableException> {
            ObjectOutputStream(ByteArrayOutputStream()).writeObject(TestFailure())
        }
    }
}
