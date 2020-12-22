package st.wing.query2fun

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceTask
import reactor.core.publisher.Mono
import st.wing.query2fun.renders.ReleaseType
import st.wing.query2fun.renders.Render
import java.io.File
import java.util.*

class QueryFunPlugin : Plugin<Project> {


    @NoArg
    data class Config(
        val host: String,
        val username: String,
        val password: String,
        val database: String,
        val path: String = "query",
        val type: ReleaseType = ReleaseType.KOTLIN,
        val port: Int,
    )


    private val propFileName = "q2f.properties"
    private fun getSourceSet(project: Project): SourceSet {
        return (project.properties["sourceSets"] as SourceSetContainer).getAt("main")
    }

    private fun getPluginConfig(sourceSet: SourceSet): Config? {
        return sourceSet.resources.files.lastOrNull {
            it.name.toLowerCase() == propFileName
        }?.let {
            Properties().let { p -> p.load(it.reader());p.to<Config>() }
        }
    }

    private fun createConnection(config: Config): Connection {
        return Mono.from(ConnectionFactories.get(builder()
            .option(DRIVER, "postgresql")
            .option(HOST, config.host)
            .option(USER, config.username)
            .option(PASSWORD, config.password)
            .option(DATABASE, config.database) // optional
            .option(PORT, config.port)
            .build()).create())
            .block()
    }

    private fun enumQuery(path: File, dir: String): Map<String, Map<String, String>> {
        path.resolve(dir).let {
            return if (it.exists()) {
                it.listFiles().filter { file ->
                    file.isDirectory
                }.map { dir ->
                    Pair<String, Map<String, String>>(
                        dir.name,
                        dir.listFiles().filter { file -> file.isFile && file.extension.toLowerCase() == "sql" }
                            .map { file -> Pair(file.nameWithoutExtension, file.readText()) }.toMap()
                    )
                }.filter { p -> p.second.isNotEmpty() }
                    .toMap()
            } else mapOf()
        }
    }

    private fun mkdir(project: Project, sourceSet: SourceSet, type: ReleaseType): String {
        sourceSet.allSource.srcDirs.let { src ->
            val group = project.group.toString()
            (src.lastOrNull { it.name.equals(type.toString(), ignoreCase = true) } ?: src.last())
                .let { dir ->
                    dir.resolve(group
                        .replace(".", "/")).let { if (it.exists()) it else dir }.let {
                        Optional.ofNullable(it.listFiles()).orElse(listOf(it).toTypedArray())
                    }
                        .first { it.isDirectory }
                        .let {
                            return if (it.resolve("generated").let { gen ->
                                    gen.mkdirs() || gen.exists()
                                }
                            ) "${group}.${it.name}.generated" else "$group.${it.name}"
                        }
                }
        }
    }

    private fun getOutputDir(
        project: Project,
        sourceSet: SourceSet,
        type: ReleaseType,
        pkg: String,
    ): (String) -> File {
        sourceSet.allSource.srcDirs.let { src ->
            val group = project.group.toString()
            (src.lastOrNull { it.name.equals(type.toString(), ignoreCase = true) } ?: src.last())
                .let { dir ->
                    dir.resolve(pkg.replace(".", "/"))
                }
        }.let {
            return { name: String -> it.resolve("$name.kt") }
        }
    }

    override fun apply(project: Project) {
        getSourceSet(project).let {
            getPluginConfig(it)
                ?.let { config ->
                    val pkg = mkdir(project, it, config.type)
                    val connection = createConnection(config)

                    var getFile = getOutputDir(project, it, config.type, pkg)
                    val (fileName, tmp, fileContent) = enumQuery(it.resources.sourceDirectories.first { i -> i.isDirectory },
                        config.path).entries.map { (fileName, sql) ->
                        println(sql)
                        println(fileName)
                        val result = Render.one(fileName, pkg, sql, connection)
                        getFile(result.first).let { file ->
                            if (!file.exists()) file.writeText(result.third)
                        }
                        result
                    }.last()

                    val task: SourceTask = project.tasks.getByName("compileKotlin") as SourceTask
                    project.buildDir.resolve("tmp/lib").let { dir ->
                        !dir.exists() && dir.mkdirs()
                        dir.resolve("MapperStorage.kt")
                    }.let { file ->
                        file.writeText(tmp)
                        task.source(file)
                        task.doLast {
                            file.delete()
                        }
                    }
                }
        }
    }
}