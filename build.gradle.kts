plugins {
    java
}

group = "com.voidguard"
version = "1.3.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // En düşük desteklenen API (1.13.2) ile derlenir; 1.13 - 1.21.x üzerinde çalışır. Sonradan eklenen
    // API'ler yalnızca Compat sınıfında yansıma ile kullanılır (NoSuchMethodError oluşmaz).
    compileOnly("com.destroystokyo.paper:paper-api:1.13.2-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)          // Java 8 bayt kodu: 1.13 sunucuları (Java 8+) dahil hepsinde yüklenir
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

// JUnit gerektirmeyen çekirdek test paketi:  ./gradlew coreTests
tasks.register<JavaExec>("coreTests") {
    group = "verification"
    description = "Bukkit'ten bağımsız tespit mantığı testleri (vanilla fizik simülasyonu dahil)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.voidguard.anticheat.core.CoreTests")
    jvmArgs("-Dstdout.encoding=UTF-8")
}
tasks.named("check") { dependsOn("coreTests") }
