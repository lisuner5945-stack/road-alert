pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RoadAlert"
include(":app")

/*
 * Путь проекта содержит кириллицу (…\Рабочий стол\Антирадар).
 * Windows-инструменты и форкнутые JVM Gradle передают такие пути в системной
 * кодировке и ломаются: тестовый worker не находит классы, aapt2 не открывает
 * файлы. Исходники при этом компилируются нормально, поэтому переносим только
 * каталог сборки в ASCII-путь. Для ASCII-проектов поведение не меняется.
 */
val projectPathIsAscii = rootDir.absolutePath.all { it.code < 128 }
if (!projectPathIsAscii) {
    // Каталог сборки обязан лежать на том же диске, что и проект:
    // AGP вычисляет относительные пути между ними.
    val safeRoot = File(rootDir.toPath().root.toFile(), "roadalert-build")
    gradle.beforeProject(object : Action<Project> {
        override fun execute(project: Project) {
            val relative = project.path.replace(':', '_').trim('_').ifEmpty { "root" }
            project.layout.buildDirectory.set(File(safeRoot, relative))
        }
    })
    logger.lifecycle("Каталог сборки перенесён в ASCII-путь: $safeRoot")
}
