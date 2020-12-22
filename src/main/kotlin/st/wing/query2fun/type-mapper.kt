package st.wing.query2fun

import java.time.LocalDateTime

class TypeMapper {
    companion object {
        private val map: Map<String, Class<out Any>> = mapOf(
            "double" to Double::class.java,
            "long" to Long::class.java,
            "int" to Int::class.java,
            "string" to String::class.java,
            "date" to LocalDateTime::class.java
        )

        fun get(strType: String): Class<out Any> {
            println(strType)
            return (map[strType] ?: String::class.java)
        }

        fun get(type: Class<out Any>): String {
            return map.entries.firstOrNull {
                it.value == type
            }?.key ?: "string"
        }


        fun <T> getDefaultValue(type: Class<T>): T {
            return when (type) {
                Double::class.java -> 1.0
                Long::class.java -> 1
                Int::class.java -> 1
                String::class.java -> "1"
                LocalDateTime::class.java -> LocalDateTime.now()
                else -> "1"
            } as T
        }

        fun getDefaultValue(strType: String): Any {
            return getDefaultValue(get(strType))
        }

        inline fun <reified T> getDefaultValue(): T {
            return getDefaultValue(T::class.java)
        }


    }
}