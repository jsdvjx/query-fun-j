package st.wing.query2fun.resolver

import io.r2dbc.spi.Connection
import java.io.File
import java.net.JarURLConnection

class DirResolver {
    data class SqlModule(
        val name: String,
        val map: Map<String, SqlResolver.SqlInfo>,
    )

    companion object {
        fun get(path: String, connection: Connection): List<SqlModule> {
            ClassLoader.getSystemClassLoader().getResource(path).let {
                return when (it.protocol) {
                    "file" -> resolveFile(File(it.path), connection)
                    "jar" -> resolveJar(it.openConnection() as JarURLConnection, connection)
                    else -> resolveFile(File(it.path), connection)
                }
            }
        }

        private fun resolveFile(dir: File, connection: Connection): List<SqlModule> {
            return if (dir.isDirectory) {
                dir.listFiles()
                    .filter { it.isDirectory }
                    .mapNotNull {
                        it.listFiles()
                            .filter { file -> file.extension.toLowerCase() == "sql" }
                            .map { file ->
                                Pair(file.nameWithoutExtension,
                                    SqlResolver.get(file.readText(), file.nameWithoutExtension, connection))
                            }
                            .toMap().let { map ->
                                if (map.isEmpty()) null
                                SqlModule(it.nameWithoutExtension, map)
                            }
                    }
            } else listOf()
        }

        private fun resolveJar(conn: JarURLConnection, connection: Connection): List<SqlModule> {
            return conn.jarFile.entries()
                .toList()
                .filter { it.name.toLowerCase().endsWith(".sql") }
                .groupBy { it.name.replace(Regex("/[\\w]*\\.sql"), "") }
                .entries.map { (path, it) ->
                    SqlModule(
                        path.split("/").last(),
                        it.map { sql ->
                            val name = sql.name.split("/").last().replace(Regex(".sql"), "")
                            Pair(
                                name,
                                SqlResolver.get(String(ClassLoader.getSystemClassLoader().getResourceAsStream(sql.name)
                                    .readAllBytes()), name, connection)
                            )
                        }.toMap()
                    )
                }
        }
    }
}