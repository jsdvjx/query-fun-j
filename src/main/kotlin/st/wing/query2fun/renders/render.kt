package st.wing.query2fun.renders

import com.github.mustachejava.DefaultMustacheFactory
import com.google.common.base.CaseFormat
import io.r2dbc.spi.Connection
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import st.wing.query2fun.firstUp
import st.wing.query2fun.resolver.SqlResolver
import java.io.StringWriter

data class DataOption(
    val name: String,
    val parameters: List<Parameter>,
)

data class Parameter(
    val name: String,
    val type: String,
    val __last: Boolean = false,
)

data class InterfaceOption(
    val name: String,
    val packageName: String,
    val imports: List<String>,
    val methods: List<String>,
    val data: List<String>,
)

data class MethodOption(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: String,
    val batchResult: Boolean,
)


enum class ReleaseType {
    JAVA,
    KOTLIN
}

class Render(
    private val type: ReleaseType,
    val name: String,
) {
    data class Pack(
        val method: String,
        val data: String,
        val imports: List<String>,
        val info: SqlResolver.SqlInfo,
    )

    private val path = """templates/${name}.${
        type.toString().toLowerCase()
    }.mustache"""
    private val mustache = DefaultMustacheFactory().compile(path)
    fun <I> compile(param: I, transform: (I) -> Any = { i: I -> i as Any }): String {
        return StringWriter().let {
            mustache.execute(it, transform(param))
            it.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&#13;&#10;", "\n")
        }
    }

    companion object {
        private val instances: Map<String, Render> by lazy {
            mapOf(
                "data" to Render(ReleaseType.KOTLIN, "data-class"),
                "method" to Render(ReleaseType.KOTLIN, "method"),
                "interface" to Render(ReleaseType.KOTLIN, "query-interface"),
                "storage" to Render(ReleaseType.KOTLIN, "map-storage")
            )
        }

        private fun getInstance(name: String): Render {
            return instances[name]!!
        }

        private fun getData(option: DataOption): String {
            return getInstance("data").compile(option)
        }

        private fun getInterface(option: InterfaceOption): String {
            return getInstance("interface").compile(option)
        }

        private fun getFileName(name: String): String {
            return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, "${name}Mapper")
        }

        private fun fieldToParameter(parameter: SqlResolver.Field): Parameter {
            return Parameter(
                parameter.name,
                getTypeName(parameter.type),
                parameter.index == parameter.total - 1
            )
        }

        private fun getTypeName(type: Class<*>): String {
            return if (type.simpleName == "int") "Int" else type.simpleName
        }

        private fun sqlReturnFieldToP(field: SqlResolver.ReturnField, last: Boolean): Parameter {
            return Parameter(
                field.name,
                getTypeName(field.type),
                last
            )
        }

        private fun getImports(vararg fields: SqlResolver.Field): List<String> {
            return fields.map { it.type.name }.filter { it.contains(".") }
        }

        fun one(
            name: String,
            pkgName: String,
            sqlMap: Map<String, String>,
            connection: Connection,
        ): Triple<String, String, String> {
            val imports: MutableSet<String> = mutableSetOf()
            val methods: MutableList<String> = mutableListOf()
            val data: MutableList<String> = mutableListOf()
            val fileName = getFileName(name)
            val compileSqlMap = mutableListOf<Parameter>()
            sqlMap.entries.forEachIndexed { index, (sqlName, content) ->
                SqlResolver.get(content, sqlName, connection).let {
                    compileSqlMap.add(Parameter("${pkgName}.${fileName}.${sqlName}",
                        it.sql,
                        index == sqlMap.entries.size - 1)
                    )
                    methods.add(getInstance("method").compile(MethodOption(
                        it.name,
                        it.parameters.map(this::fieldToParameter),
                        it.returnType.name,
                        it.batchResult
                    )))
                    val fields = it.returnType.fields.block()
                    data.add(getInstance("data").compile(DataOption(
                        it.returnType.name,
                        fields.map(this::fieldToParameter)
                    )))
                    imports.addAll(getImports(*fields.toTypedArray(), *it.parameters.toTypedArray()))
                    imports.add(Flux::class.java.name)
                    imports.add(Mono::class.java.name)
                }
            }
            return Triple(fileName,
                getInstance("storage").compile(mapOf("sqlMap" to compileSqlMap, "pkg" to pkgName)),
                getInterface(InterfaceOption(
                    name.firstUp(),
                    pkgName,
                    imports.toList(),
                    methods,
                    data
                )))
        }

//        fun dir(path: String, pkgName: String, connection: Connection): Map<String, String> {
//            return DirResolver.get(path, connection).map {
//                val methods = it.map.mapNotNull { (name, info) ->
//                    getInstance("method").compile(MethodOption(
//                        name,
//                        info.parameters.mapIndexed { index, p ->
//                            Parameter(p.name,
//                                if (p.type.simpleName == "int") "Int" else p.type.simpleName,
//                                index == info.parameters.size - 1)
//                        },
//                        info.returnType.name,
//                        info.batchResult
//                    ))
//                }
//                val imports = it.map.flatMap { (_, info) ->
//                    mutableListOf(Flux::class.java.name, Mono::class.java.name).let { base ->
//                        base.addAll(info.parameters.map { p -> p.type.name }.filter { t -> t.contains(".") })
//                        base.addAll(fields.map { f -> f.type.name }.filter { t -> t.contains(".") })
//                        base
//                    }
//                }
//
//                val data = it.map.map { (_, info) ->
//                    getData(DataOption(info.returnType.name, info.returnType.fields.mapIndexed { index, f ->
//                        Parameter(f.name,
//                            if (f.type.simpleName == "int") "Int" else f.type.simpleName,
//                            index == info.returnType.fields.size - 1)
//                    }))
//                }
//                Pair(getPath(pkgName, it.name), getInterface(InterfaceOption(
//                    it.name,
//                    pkgName,
//                    imports.toSet().toList(),
//                    methods,
//                    data
//                )))
//            }.toMap()
//        }
    }
}