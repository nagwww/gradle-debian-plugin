import io.freefair.gradle.plugins.maven.central.ValidateMavenPom
import java.text.SimpleDateFormat
import java.util.*

plugins {
  id("groovy")
  id("java-gradle-plugin")
  id("maven-publish")
  id("signing")
  id("com.github.ben-manes.versions") version "0.44.0"
  id("net.ossindex.audit") version "0.4.11"
  id("com.gradle.plugin-publish") version "1.0.0"
  id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
  id("io.freefair.maven-central.validate-poms") version "6.5.1"
}

val dependencyVersions = listOf<String>(
)

repositories {
  mavenCentral()
}

configurations.all {
  resolutionStrategy {
    failOnVersionConflict()
    force(dependencyVersions)
  }
}

dependencies {
  constraints {
    listOf(
      "org.apache.maven:maven-archiver",
      "org.apache.maven:maven-artifact",
      "org.apache.maven:maven-core",
      "org.apache.maven:maven-model",
      "org.apache.maven:maven-plugin-api",
    ).forEach {
      implementation(it) {
        version {
          strictly("[3,)")
          prefer("3.8.5")
        }
      }
    }
    implementation("org.apache.maven.shared:maven-shared-utils") {
      version {
        strictly("[3,)")
        prefer("3.3.4")
      }
    }
    implementation("org.apache.commons:commons-compress") {
      version {
        strictly("[1.20,)")
        prefer("1.21")
      }
    }
    implementation("commons-io:commons-io") {
      version {
        strictly("[2,3)")
        prefer("2.11.0")
      }
    }
    implementation("org.codehaus.plexus:plexus-classworlds") {
      version {
        strictly("[2.5,)")
        prefer("2.6.0")
      }
    }
    implementation("org.codehaus.plexus:plexus-component-annotations") {
      version {
        strictly("[2,)")
        prefer("2.1.0")
      }
    }
    implementation("org.codehaus.plexus:plexus-utils") {
      version {
        strictly("[3,)")
        prefer("3.4.1")
      }
    }
    implementation("org.slf4j:slf4j-api") {
      version {
        strictly("[1.7,)")
        prefer("1.7.36")
      }
    }
  }

  api(gradleApi())
  api(localGroovy())

  api("org.vafer:jdeb:1.10")
  implementation("commons-lang:commons-lang:2.6")

  testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
  testImplementation("cglib:cglib-nodep:3.3.0")

  // see https://docs.gradle.org/current/userguide/test_kit.html
  //testImplementation(gradleTestKit())
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
  withType(Test::class.java) {
    useJUnitPlatform()
  }
}

val javadocJar by tasks.registering(Jar::class) {
  dependsOn("classes")
  archiveClassifier.set("javadoc")
  from(tasks.javadoc)
}

val sourcesJar by tasks.registering(Jar::class) {
  dependsOn("classes")
  archiveClassifier.set("sources")
  from(sourceSets.main.get().allSource)
}

artifacts {
  add("archives", sourcesJar.get())
  add("archives", javadocJar.get())
}

fun findProperty(s: String) = project.findProperty(s) as String?

val gitHubPackagesRepositoryName = "GitHubPackages"
val isSnapshot = project.version == "unspecified"
val artifactVersion = if (!isSnapshot) project.version as String else SimpleDateFormat("yyyy-MM-dd\'T\'HH-mm-ss").format(Date())!!
val publicationName = "gradleDebianPlugin"
publishing {
  repositories {
    maven {
      name = gitHubPackagesRepositoryName
      url = uri("https://maven.pkg.github.com/${property("github.package-registry.owner")}/${property("github.package-registry.repository")}")
      credentials {
        username = System.getenv("GITHUB_ACTOR") ?: findProperty("github.package-registry.username")
        password = System.getenv("GITHUB_TOKEN") ?: findProperty("github.package-registry.password")
      }
    }
  }
  publications {
    register(publicationName, MavenPublication::class) {
      pom {
        name.set("gradle-debian-plugin")
        description.set("A Debian plugin for Gradle")
        url.set("https://github.com/gesellix/gradle-debian-plugin")
        licenses {
          license {
            name.set("MIT")
            url.set("https://opensource.org/licenses/MIT")
          }
        }
        developers {
          developer {
            id.set("gesellix")
            name.set("Tobias Gesellchen")
            email.set("tobias@gesellix.de")
          }
        }
        scm {
          connection.set("scm:git:github.com/gesellix/gradle-debian-plugin.git")
          developerConnection.set("scm:git:ssh://github.com/gesellix/gradle-debian-plugin.git")
          url.set("https://github.com/gesellix/gradle-debian-plugin")
        }
      }
      artifactId = "gradle-debian-plugin"
      version = artifactVersion
      from(components["java"])
      // TODO how do we ensure that these artifacts will always be added
      // automatically?
//      artifact(sourcesJar.get())
//      artifact(javadocJar.get())
    }
  }
}
nexusPublishing {
  repositories {
    if (!isSnapshot) {
      sonatype {
        // 'sonatype' is pre-configured for Sonatype Nexus (OSSRH) which is used for The Central Repository
        stagingProfileId.set(System.getenv("SONATYPE_STAGING_PROFILE_ID") ?: findProperty("sonatype.staging.profile.id")) //can reduce execution time by even 10 seconds
        username.set(System.getenv("SONATYPE_USERNAME") ?: findProperty("sonatype.username"))
        password.set(System.getenv("SONATYPE_PASSWORD") ?: findProperty("sonatype.password"))
      }
    }
  }
}

signing {
  setRequired({ !isSnapshot })
  val signingKey: String? by project
  val signingPassword: String? by project
  useInMemoryPgpKeys(signingKey, signingPassword)
  sign(publishing.publications[publicationName])
}

pluginBundle {
  website = "https://github.com/gesellix/gradle-debian-plugin"
  vcsUrl = "https://github.com/gesellix/gradle-debian-plugin.git"
  tags = listOf("debian", "jdeb", "package", "ubuntu")
}

gradlePlugin {
  plugins {
    register(publicationName) {
      id = "de.gesellix.debian"
      displayName = "Gradle Debian plugin"
      description = "Create Debian packages with Gradle"
      implementationClass = "de.gesellix.gradle.debian.DebianPackagePlugin"
      version = artifactVersion
    }
  }
}

tasks.withType<ValidateMavenPom>().configureEach {
  ignoreFailures = System.getenv()["IGNORE_INVALID_POMS"] == "true"
      || name.contains("For${publicationName.capitalize()}PluginMarkerMaven")
      || name.contains("ForPluginMavenPublication")
}

tasks.register("publishTo${gitHubPackagesRepositoryName}") {
  group = "publishing"
  description = "Publishes all Maven publications to the $gitHubPackagesRepositoryName Maven repository."
  dependsOn(tasks.withType<PublishToMavenRepository>().matching {
    it.repository == publishing.repositories[gitHubPackagesRepositoryName]
  })
}

val isStandardMavenPublication = { repository: MavenArtifactRepository, publication: MavenPublication ->
  publication == publishing.publications[publicationName]
      && repository.name in listOf("sonatype", gitHubPackagesRepositoryName)
}
val isGradlePluginPublish = { repository: MavenArtifactRepository, publication: MavenPublication ->
  publication == publishing.publications["pluginMaven"]
      && repository.name !in listOf("sonatype", gitHubPackagesRepositoryName)
}

tasks.withType<PublishToMavenRepository>().configureEach {
  onlyIf {
    isStandardMavenPublication(repository, publication)
        || isGradlePluginPublish(repository, publication)
  }
}
