package st.wing.query2fun

import java.io.File
import java.net.JarURLConnection
import java.util.jar.JarEntry
import java.util.jar.JarFile

class ClassPath {
    companion object {
        private val classMap: Map<String, Class<Any>> = mutableMapOf()
        inline fun <reified T> getClassWithAnnotation(pkgName: String): List<Class<*>> {
            return getClass(pkgName).filter {
                it.annotations.map { i -> println(i.annotationClass.qualifiedName);i }
                    .any { annotation -> annotation.annotationClass.qualifiedName == T::class.java.name }
            }
        }

        fun getClass(pkgName: String, filter: (String) -> Boolean = { _ -> true }): List<Class<*>> {
            Thread.currentThread().contextClassLoader
                .getResource(pkgName.replace(".", "/"))
                .let {
                    return when (it.protocol) {
                        "file" -> getClassByFile(File(it.file), pkgName)
                        "jar" -> getClassByJar(it.openConnection() as JarURLConnection)
                        else -> listOf()
                    }.filter(filter).mapNotNull { name ->
                        println(name)
                        try {
                            Class.forName(name)
                        } catch (t: Throwable) {
                            null
                        }
                    }
                }
        }

        private fun getClassByJar(connect: JarURLConnection): List<String> {
            return connect.jarFile.entries().toList().filter {
                it.name.toLowerCase().endsWith(".class")
            }.map {
                it.name.substring(0, it.name.length - 6).replace('/', '.')
            }
        }

        private fun getJarEntry(connect: JarURLConnection): Pair<JarFile, List<JarEntry>> {
            return Pair(connect.jarFile, connect.jarFile.entries().toList().filter {
                it.name.toLowerCase().endsWith(".class")
            })
        }

        private fun getClassFile(file: File, filter: (File) -> Boolean): File? {
            if (file.isDirectory) {
                file.listFiles()
                    .filter { it.isDirectory || it.extension == "class" }
                    .groupBy { it.isDirectory }
                    .forEach { (isDir, fs) ->
                        when (isDir) {
                            true -> fs.forEach { dir ->
                                getClassFile(dir, filter)
                            }
                            false -> fs.firstOrNull(filter)?.let {
                                return@getClassFile it
                            }
                        }
                    }
            }
            return null
        }

        private fun getClassByFile(file: File, prefix: String = ""): List<String> {
            val classes: MutableList<String> = mutableListOf()
            val getPrefix = { p: String -> if (prefix.isEmpty()) p else "${prefix}.${p}" }
            if (file.isDirectory) {
                file.listFiles()
                    .filter { it.isDirectory || it.extension == "class" }
                    .groupBy { it.isDirectory }
                    .forEach { (isDir, fs) ->
                        when (isDir) {
                            true -> fs.forEach { dir ->
                                classes.addAll(getClassByFile(dir, getPrefix(file.name)))
                            }
                            false -> fs.forEach { child ->
                                classes.add(getPrefix(child.nameWithoutExtension))
                            }
                        }
                    }
            }
            return classes
        }

        fun getBin(pkgName: String, className: String): ByteArray {
            Thread.currentThread().contextClassLoader
                .getResource(pkgName.replace(".", "/"))
                .let {
                    return@getBin if (it.protocol == "jar") {
                        val conn = it.openConnection() as JarURLConnection
                        val entry = conn.jarFile.entries().toList()
                            .firstOrNull { jar -> jar.name.endsWith("$className.class", ignoreCase = true) }
                        println(entry)
                        conn.jarFile.getInputStream(entry).readAllBytes()
                    } else {
                        getClassFile(File(it.file)) { file ->
                            file.nameWithoutExtension.endsWith(className, ignoreCase = true)
                        }!!.let { file ->
                            file.readBytes()
                        }
                    }
//                    return when (it.protocol) {
//                        "file" -> getClassFile(File(it.file)).first { file ->
//                            file.nameWithoutExtension.endsWith(className, ignoreCase = true)
//                        }.bufferedReader()
//                        "jar" -> getJarEntry(it.openConnection() as JarURLConnection)
//                        else -> null
//                    }
                }
        }
    }
}
