plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "dev.hemendra"
version = "0.1.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2024.2.5")
        instrumentationTools()
        zipSigner()
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Claude Watch"
        vendor {
            name = "Hemendra"
            email = "hemendra7011@gmail.com"
            url = "https://github.com/Hemendra1990/claude-watch"
        }
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "261.*"
        }
    }

    // Signing: required by JetBrains Marketplace. Reads cert/key from signing/ (gitignored);
    // password from env PRIVATE_KEY_PASSWORD or -Psigning.password. Absent keys do not break
    // a normal `buildPlugin` (only signPlugin/publishPlugin need them).
    signing {
        certificateChainFile = layout.projectDirectory.file("signing/chain.crt")
        privateKeyFile = layout.projectDirectory.file("signing/private.pem")
        // Key is unencrypted (the forked signer JVM lacks providers to decrypt PKCS#8/PKCS#1
        // encrypted keys). PRIVATE_KEY_PASSWORD is wired but only used if you supply an
        // encrypted key on a JVM that supports it.
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
            .orElse(providers.gradleProperty("signing.password"))
            .orElse("")
    }

    // Publishing: token from env PUBLISH_TOKEN or -Ppublish.token. `channels` default = stable.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
            .orElse(providers.gradleProperty("publish.token"))
    }

    // `verifyPlugin` runs the same JetBrains Plugin Verifier the Marketplace runs.
    pluginVerification {
        ides {
            ide("IC", "2024.2.5")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
