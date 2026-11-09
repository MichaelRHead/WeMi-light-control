// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

val ensureDebugKeystore by tasks.registering {
  val keystoreFile = layout.projectDirectory.file("debug.keystore").asFile
  outputs.file(keystoreFile)
  onlyIf { !keystoreFile.exists() }
  doLast {
    val javaHome = System.getProperty("java.home")
    val keytoolBin = File(javaHome, "bin/keytool").takeIf { it.exists() }?.absolutePath ?: "keytool"
    ProcessBuilder(
      keytoolBin,
      "-genkeypair", "-v",
      "-keystore", keystoreFile.absolutePath,
      "-storepass", "android",
      "-alias", "androiddebugkey",
      "-keypass", "android",
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "10000",
      "-dname", "CN=Android Debug,O=Android,C=US"
    ).inheritIO().start().waitFor()
  }
}

subprojects {
  tasks.configureEach {
    if (name == "validateSigningDebug" || name == "packageDebug") {
      dependsOn(ensureDebugKeystore)
    }
  }
}

