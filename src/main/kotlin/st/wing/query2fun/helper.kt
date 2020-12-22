package st.wing.query2fun

import com.google.common.base.CaseFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType

inline infix fun Any.conv(field: KParameter): Any {
    if (this::class.java != field.type) {
        return this conv if (field.type.javaType.typeName == "int") Int::class.java else Class.forName(field.type.javaType.typeName)
    }
    return this
}

inline infix fun Any.conv(clazz: Class<*>): Any {
    return when (clazz) {
        Int::class.java -> this.toString().toInt()
        Double::class.java -> this.toString().toDouble()
        Float::class.java -> this.toString().toFloat()
        LocalDateTime::class.java -> this.toString().toTime()
        String::class.java -> this.toString()
        else -> {
            return when {
                clazz.isEnum -> clazz.enumConstants[this.toString()
                    .toInt()]
                else -> this
            }
        }
    }
}

inline fun <reified T> Properties.to(): T? {
    return (this.toMap() as Map<String, Any?>).to<T>()
}

inline fun <reified T> Map<String, Any?>.to(): T? {
    val defMarker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker").declaredConstructors.last()
    defMarker.isAccessible = true
    val constructor = T::class.java.constructors.lastOrNull {
        if (it.parameters.isNotEmpty()) it.parameters.last()!!.type == Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        else false
    }
    var mark = 0
    return if (constructor != null)
        T::class.constructors.first { i ->
            i.parameters.all { kp ->
                if (constructor.parameterCount > 0) constructor.parameters[kp.index].type.name == kp.type.javaType.typeName
                else false
            }
        }.parameters.map { kp ->
            if (this[kp.name] == null) {
                if (kp.isOptional) {
                    mark = mark or (1 shl kp.index)
                    null
                } else Throwable("${kp.name} must required!")
            } else {
                this[kp.name]!! conv kp
            }
        }.toTypedArray().let {
            constructor.newInstance(*it, mark, defMarker.newInstance()) as T
        }
    else null
}

inline fun String.toTime(): LocalDateTime {
    return LocalDateTime.parse(
        this,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )
}

inline fun LocalDateTime.toStr(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    return this.format(DateTimeFormatter.ofPattern(pattern))
}

inline infix fun String.extract(regex: Regex): List<String> {
    return regex.findAll(this).map { it.groupValues.last() }.toList()
}

fun String.extract(): List<String> {
    return this extract Regex("""\$\{([a-zA-Z_][\da-zA-Z_]*?)}""")
}

fun String.firstUp(fromFormat: CaseFormat = CaseFormat.LOWER_CAMEL): String {
    return fromFormat.to(CaseFormat.UPPER_CAMEL, this)
}

