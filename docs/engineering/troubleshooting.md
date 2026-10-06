# Engineering: Troubleshooting

This guide assists software engineers in diagnosing and resolving build, compilation, modularity, and native binding issues during development of **MMediatar**.

## Build and Compiler Issues

### 1. `javac: invalid flag: --release 11`

**Symptom:**  
`mvn clean compile` fails immediately citing an invalid compiler release flag.

**Cause:**  
Maven is executing with an ancient JDK (Java 8 or earlier) that does not support the `--release` cross-compilation flag introduced in Java 9.

**Solution:**  
Point your `JAVA_HOME` to an installed OpenJDK 11 or higher runtime:
```bash
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
mvn clean compile
```

---

### 2. `Error: JavaFX runtime components are missing, and are required to run this application`

**Symptom:**  
Running `java -jar target/Mediatar-1.0-SNAPSHOT.jar` fails with the classic JavaFX runtime components missing error.

**Cause:**  
JavaFX was decoupled from the JDK in Java 11. When executing a JAR directly without the JavaFX plugin, the `--module-path` must be explicitly specified unless using the `App.java` non-modular fallback launcher.

**Solution:**  
Always run through the JavaFX Maven plugin during development:
```bash
mvn javafx:run
```
Or launch via `com.nicch.mediatar.App` directly with module arguments:
```bash
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml,javafx.media \
     -cp target/Mediatar-1.0-SNAPSHOT.jar \
     com.nicch.mediatar.App
```

---

### 3. `java.lang.module.FindException: Module com.nicch.mediatar not found`

**Symptom:**  
Modular launch fails during `jlink` or JPMS verification.

**Cause:**  
The classes were compiled without `module-info.java` or `pom.xml` does not include `src/main/java` as a source root.

**Solution:**  
1. Verify `src/main/java/module-info.java` exists.
2. Clean and recompile:
   ```bash
   mvn clean compile
   ```

---

### 4. Scene Builder FXML Loading Exceptions

**Symptom:**  
Gluon Scene Builder refuses to open `mainworkhorse.fxml` or displays red placeholder boxes.

**Cause:**  
Custom JavaFX components (like `TimelinePane`) cannot be instantiated by Scene Builder without the compiled classpath.

**Solution:**  
In Scene Builder, navigate to **Custom Library Manager** → **Add JAR/FXML** and add `target/Mediatar-1.0-SNAPSHOT.jar`. This informs Scene Builder how to render custom widgets.

---

### 5. `UnsatisfiedLinkError: libgstreamer-1.0.so`

**Symptom:**  
JVM crashes on `Platform.startup()` with an `UnsatisfiedLinkError` pointing to native GStreamer libraries.

**Cause:**  
GStreamer native shared objects cannot be located on `java.library.path`.

**Solution:**  
Verify the location of `libgstreamer-1.0.so` and pass it to Maven:
```bash
mvn javafx:run -Doptions="-Djava.library.path=$(dirname $(find /usr/lib* -name 'libgstreamer-1.0.so*' | head -n1))"
```

For security policies and threat defenses, see [security.md](security.md).
