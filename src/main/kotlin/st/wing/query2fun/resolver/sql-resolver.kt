package st.wing.query2fun.resolver

import com.google.common.base.CaseFormat
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import st.wing.query2fun.TypeMapper
import st.wing.query2fun.conv
import st.wing.query2fun.extract

class SqlResolver {
    interface Field {
        val type: Class<*>;
        val name: String;
        val index: Int;
        val total: Int;
    }

    data class SqlInfoParameter(
        override val type: Class<*>,
        val default: Any,
        override val name: String,
        override val index: Int,
        override val total: Int,
    ) : Field

    data class ReturnField(
        override val type: Class<*>,
        override val name: String,
        override val index: Int,
        override val total: Int,
    ) : Field

    data class SqlInfoReturnType(
        val name: String,
        val fields: Mono<List<ReturnField>>,
    )

    data class SqlInfo(
        val name: String,
        val sql: String,
        val parameters: List<SqlInfoParameter>,
        var returnType: SqlInfoReturnType,
        val type: SqlType = SqlType.SELECT,
        val batchParameter: Boolean = false,
        val batchResult: Boolean = false,
        val limited: Boolean = false,
    )

    enum class SqlType(val type: String) {
        INSERT("insert"),
        UPDATE("update"),
        DELETE("delete"),
        SELECT("select")
    }


    companion object {

        private fun getType(sql: String, annotations: Map<String, List<String>>): SqlType {
            return (annotations["OPTIONS"]?.firstOrNull {
                listOf("insert",
                    "update",
                    "select",
                    "delete").contains(it.toLowerCase())
            }
                ?: sql.split(" ")
                    .firstOrNull { it.trim().isNotEmpty() }
                ?: "select")
                .toLowerCase()
                .let {
                    SqlType.valueOf(it.toUpperCase())
                }

        }

        private fun getBatchResult(sql: String, annotations: Map<String, List<String>>): Boolean {
            //除非手动指定，否则只有select类型是批量返回
            return annotations["OPTIONS"]?.contains("BATCH_RESULT") == true || getType(sql,
                annotations) == SqlType.SELECT
        }

        private fun getParameterNames(sql: String): List<String> {
            return sql.extract()
        }

        private fun getParameterTypes(annotations: Map<String, List<String>>, size: Int): List<Class<out Any>> {
            return (0 until size).map { index ->
                annotations["TYPE"]?.let {
                    TypeMapper.get(it[index])
                } ?: String::class.java
            }
        }

        private fun getParameterDefaults(annotations: Map<String, List<String>>, types: List<Class<out Any>>): List<*> {
            return types.mapIndexed { index, type ->
                annotations["DEFAULT"]?.let {
                    try {
                        it[index] conv type
                    } catch (t: Throwable) {
                        TypeMapper.getDefaultValue(type)
                    }
                } ?: TypeMapper.getDefaultValue(type)
            }
        }

        private fun getParameters(sql: String, annotations: Map<String, List<String>>): List<SqlInfoParameter> {
            return getParameterNames(sql).let {
                val types = getParameterTypes(annotations, it.size)
                val defs = getParameterDefaults(annotations, types)
                it.mapIndexed { index, name ->
                    SqlInfoParameter(
                        types[index],
                        defs[index] as Any,
                        name,
                        index,
                        it.size
                    )
                }
            }
        }

        private fun getBatchParameter(sql: String, annotations: Map<String, List<String>>): Boolean {
            //保留可能性，只有insert是可以是批量操作
            return getType(sql, annotations) == SqlType.INSERT
        }

        private fun getAnnotation(annotation: String): Pair<String, List<String>> {
            return annotation.split("#").let {
                Pair(it[0], it.subList(1, it.size).joinToString("")
                    .split(Regex("(?<!\\\\),"))
                    .map { str -> str.replace("\\,", ",") }
                )
            }
        }

        private fun getAnnotations(content: List<String>): Map<String, List<String>> {
            return content.map { getAnnotation(it) }.toMap()
        }

        private fun getLimited(annotations: Map<String, List<String>>): Boolean {
            return (annotations["OPTIONS"] ?: listOf()).any { it.contains("LIMIT") }
        }

        private fun compileSql(sql: String, parameters: List<SqlInfoParameter>): String {
            return parameters.fold(sql) { acc, parameter ->
                acc.replace("\${${parameter.name}}", "$${parameter.index + 1}")
            }
        }

        private fun getParameters(row: Row, metadata: RowMetadata): List<SqlResolver.ReturnField> {
            return metadata.columnNames.mapIndexed { index, name ->
                SqlResolver.ReturnField(
                    metadata.getColumnMetadata(name).javaType,
                    name,
                    index,
                    metadata.columnNames.size
                )
            }
        }

        fun query(
            connection: Connection,
            sql: String,
            limited: Boolean,
            parameters: List<SqlInfoParameter>,
            args: List<Any>,
        ): Mono<List<ReturnField>> {
            return connection.createStatement("${sql} ${if (limited) "limit 1" else ""}").let {
                parameters.forEach { parameter ->
                    it.bind(
                        "$${parameter.index + 1}",
                        if (parameter.index > args.size - 1) parameter.default else args[parameter.index]
                    )
                }
                return it.execute().toMono().log().flatMap { result ->
                    result.map { row, meta -> getParameters(row, meta) }.toMono()
                }
            }
        }


        fun get(content: String, name: String, connection: Connection): SqlInfo {
            return content.split(Regex("[\r\n]+")).let {
                it.groupBy { i -> if (i.startsWith("--")) "annotation" else "sql" }
                    .let { map ->
                        val annotations =
                            getAnnotations(map["annotation"]!!.map { str -> str.replace(Regex("^--"), "") })
                        val sql = map["sql"]!!.joinToString(" ")
                        val parameters = getParameters(sql, annotations)
                        val compileSql = compileSql(sql, parameters)
                        val limited = getLimited(annotations)

                        SqlInfo(
                            name,
                            compileSql,
                            parameters,
                            SqlInfoReturnType("${CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name)}Result",
                                query(connection,
                                    compileSql,
                                    limited,
                                    parameters,
                                    parameters.map { p -> p.default })),
                            getType(sql, annotations),
                            getBatchParameter(sql, annotations),
                            getBatchResult(sql, annotations),
                            getLimited(annotations)
                        )
                    }
            }
        }
    }
}