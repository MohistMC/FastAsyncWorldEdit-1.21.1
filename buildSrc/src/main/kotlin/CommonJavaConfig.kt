import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType

fun Project.applyCommonJavaConfiguration(sourcesJar: Boolean, banSlf4j: Boolean = true) {
    applyCommonConfiguration()
    apply(plugin = "eclipse")
    apply(plugin = "idea")

    tasks
            .withType<JavaCompile>()
            .matching { it.name == "compileJava" || it.name == "compileTestJava" }
            .configureEach {
                val disabledLint = listOf(
                        "processing", "path", "fallthrough", "serial", "overloads", "this-escape",
                )
                options.release.set(21)
                options.compilerArgs.addAll(listOf("-Xlint:all") + disabledLint.map { "-Xlint:-$it" })
                options.isDeprecation = true
                options.encoding = "UTF-8"
                options.compilerArgs.add("-parameters")
                options.compilerArgs.add("--add-modules=jdk.incubator.vector")
                options.isWarnings = false
            }

    configurations.all {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform {
            includeEngines("junit-jupiter", "jqwik")
        }
    }

    dependencies {
        "compileOnly"("com.google.code.findbugs:jsr305:3.0.2")
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.11.1")
        "testImplementation"("org.junit.jupiter:junit-jupiter-params:5.11.1")
        "testImplementation"("net.jqwik:jqwik:1.9.0")
        "testImplementation"("org.mockito:mockito-core:5.14.0")
        "testImplementation"("org.mockito:mockito-junit-jupiter:5.14.0")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.11.1")
    }

    configure<JavaPluginExtension> {
        disableAutoTargetJvm()
        if (sourcesJar) {
            withSourcesJar()
        }
    }

    if (banSlf4j) {
        configurations["compileClasspath"].apply {
            resolutionStrategy.componentSelection {
                withModule("org.slf4j:slf4j-api") {
                    reject("No SLF4J allowed on compile classpath")
                }
            }
        }
    }
}
