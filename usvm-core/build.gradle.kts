plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version "1.9.22" }

dependencies {
    api(project(":usvm-util"))
    api(Libs.ksmt_core)
    api(Libs.ksmt_z3)
    api(Libs.kotlinx_collections)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:atomicfu:0.23.2")
    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.ksmt_yices)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
