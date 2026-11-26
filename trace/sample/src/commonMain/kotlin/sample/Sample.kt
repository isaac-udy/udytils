package sample

fun interface Greet {
    fun invoke(name: String): String
}

class GreetImpl : Greet {
    override fun invoke(name: String): String = "hi $name"
}

class GreetingRepository {
    val greet = Greet { name -> "hello $name" }
}

class Screen(private val greet: Greet) {
    fun onGreetClicked(): String = greet.invoke("sample")
}

fun failing(): Int = error("boom")
