package st.wing.query2fun

import io.r2dbc.spi.Connection
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*


class MapperProxy(private val connection: Connection) {
    private val sqlMap: Map<String, String> =
        Class.forName("st.wing.query2fun.QueryMapperStorage").declaredFields.first { it.name == "SQL" }.let {
            it.isAccessible = true
            it.get(null)
        } as Map<String, String>

    val instanceMap: MutableMap<Class<*>, Any> = mutableMapOf()

    inline fun <reified T> get(): T {
        return Optional.ofNullable(instanceMap[T::class.java] as T?).orElseGet {
            val result = create(T::class.java) as T
            instanceMap[T::class.java] = result!!
            result
        }
    }

    fun create(clazz: Class<*>): Any {
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz),
        ) { _, method, args ->
            val name = "${method.declaringClass.name}.${method.name}"
            queryBy(sqlMap[name]!!, method, args)
        }
    }

    private fun queryBy(sql: String, method: Method, args: Array<*>): Mono<Any> {
        return connection.createStatement(sql).let {
            method.parameters.forEachIndexed { index, _ -> it.bind("$${index + 1}", args[index]) }
            return Mono.from(it.execute()).log().flatMap { result ->
                Mono.from(result.map { row, rowMetadata ->
                })
            }
        }
    }
}