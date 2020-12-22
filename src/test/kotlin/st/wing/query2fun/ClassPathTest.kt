package st.wing.query2fun

import org.junit.Test

internal class ClassPathTest {
    @Test
    fun path() {
        ClassPath.getClass("st.wing.query2fun")
    }
}