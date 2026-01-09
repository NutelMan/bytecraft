package su.bytecraft;

import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

public class JavaCompiler {

    private VersionUtils.McVersion detectedMcVersion = null;
    private PluginVersionDetector.PluginInfo pluginInfo = null;

    // Кэш для API файлов из ресурсов
    private static Map<String, File> cachedApiFiles = new HashMap<>();
    private static File tempExtractDir = null;

    public byte[] compileJavaFile(File javaFile, List<File> classpath, File originalJar) throws Exception {
        // Определяем версию MC и информацию о плагине
        if (originalJar != null && detectedMcVersion == null) {
            pluginInfo = PluginVersionDetector.getPluginInfo(originalJar);
            detectedMcVersion = pluginInfo.mcVersion;

            System.out.println("🎯 Информация о плагине:");
            System.out.println("   📛 Имя: " + (pluginInfo.name != null ? pluginInfo.name : "Неизвестно"));
            System.out.println("   📦 Версия плагина: " + (pluginInfo.pluginVersion != null ? pluginInfo.pluginVersion : "Неизвестно"));
            System.out.println("   🎮 Версия Minecraft: " + detectedMcVersion);
            System.out.println("   🏗️  Главный класс: " + (pluginInfo.mainClass != null ? pluginInfo.mainClass : "Неизвестно"));
        }

        if (detectedMcVersion == null) {
            detectedMcVersion = new VersionUtils.McVersion("1.20");
            System.out.println("⚠️  Версия не определена, используем по умолчанию: " + detectedMcVersion);
        }

        // Создаем временную директорию
        Path tempDir = Files.createTempDirectory("compile_");

        try {
            javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new CompilationException(
                        "Java компилятор не найден!\n" +
                                "Установите JDK (не JRE):\n" +
                                "1. Скачайте с https://adoptium.net/\n" +
                                "2. Установите JDK\n" +
                                "3. Убедитесь что JAVA_HOME указывает на JDK\n" +
                                "Текущая Java: " + System.getProperty("java.version") + "\n" +
                                "Путь: " + System.getProperty("java.home")
                );
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

            // Подготавливаем опции компиляции
            List<String> options = new ArrayList<>();
            options.add("-g"); // Включаем debug информацию
            options.add("-parameters"); // Сохраняем имена параметров

            // Для старых версий Minecraft может потребоваться source/target
            if (detectedMcVersion.compareTo(new VersionUtils.McVersion("1.17")) < 0) {
                // Для версий до 1.17 используем Java 8 compatibility
                options.add("-source");
                options.add("8");
                options.add("-target");
                options.add("8");
                System.out.println("⚙️  Установлены флаги для Java 8 совместимости");
            }

            // Создаем полный classpath
            List<File> fullClasspath = new ArrayList<>();

            // 1. Добавляем стандартные библиотеки Java
            addJavaLibraries(fullClasspath);

            // 2. Добавляем пользовательский classpath
            if (classpath != null) {
                fullClasspath.addAll(classpath);
            }

            // 3. Добавляем Bukkit/Spigot API (из ресурсов JAR)
            List<File> apiDeps = findBukkitDependenciesFromResources(detectedMcVersion);
            fullClasspath.addAll(apiDeps);

            // 4. Добавляем Maven зависимости
            fullClasspath.addAll(getMavenDependencies());

            // Строим classpath строку
            if (!fullClasspath.isEmpty()) {
                String cp = fullClasspath.stream()
                        .distinct()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
                options.add("-cp");
                options.add(cp);

                System.out.println("📚 Classpath содержит " + fullClasspath.size() + " файлов:");
                System.out.println("📁 Classpath (первые 500 символов): " +
                        cp.substring(0, Math.min(cp.length(), 500)) +
                        (cp.length() > 500 ? "..." : ""));
            }

            // Целевая директория для .class файлов
            options.add("-d");
            options.add(tempDir.toString());

            // Файлы для компиляции
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(javaFile));

            // Запускаем компиляцию
            javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits
            );

            System.out.println("⚡ Запуск компиляции...");
            boolean success = task.call();

            // Выводим диагностику
            if (!success || !diagnostics.getDiagnostics().isEmpty()) {
                System.out.println("⚠️  Диагностика компиляции:");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    System.out.println("   " + diagnostic.getKind() + ": " +
                            diagnostic.getMessage(Locale.getDefault()) +
                            " at line " + diagnostic.getLineNumber());
                }
            }

            fileManager.close();

            if (!success) {
                StringBuilder error = new StringBuilder("Ошибка компиляции:\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    error.append("? Строка ").append(diagnostic.getLineNumber())
                            .append(": ").append(diagnostic.getMessage(Locale.getDefault()))
                            .append("\n");
                }
                throw new CompilationException(error.toString());
            }

            // Ищем скомпилированный .class файл
            String className = javaFile.getName().replace(".java", "");
            Path classFile = findClassFile(tempDir, className);

            if (classFile == null) {
                throw new CompilationException("Скомпилированный .class файл не найден в: " + tempDir);
            }

            System.out.println("✅ Компиляция успешна!");
            return Files.readAllBytes(classFile);

        } finally {
            deleteDirectory(tempDir);
        }
    }

    // Новый метод: поиск API файлов в ресурсах JAR
    private List<File> findBukkitDependenciesFromResources(VersionUtils.McVersion targetVersion) {
        List<File> deps = new ArrayList<>();

        System.out.println("🔍 Поиск API в ресурсах JAR для версии " + targetVersion + "...");

        try {
            // Получаем все API файлы из ресурсов
            Map<String, File> apiFiles = getApiFilesFromResources();

            if (apiFiles.isEmpty()) {
                System.out.println("⚠️  В ресурсах JAR нет API файлов!");
                System.out.println("   Проверьте что папка 'libs' находится внутри ByteCraft.jar");
                return deps;
            }

            System.out.println("📦 Найдено API файлов в ресурсах: " + apiFiles.size());

            // Конвертируем в массив для поиска лучшего совпадения
            File[] filesArray = apiFiles.values().toArray(new File[0]);

            // Находим наиболее подходящую версию
            File bestMatch = VersionUtils.findBestMatchVersion(filesArray, targetVersion);

            if (bestMatch != null) {
                deps.add(bestMatch);
                System.out.println("✅ Выбран API из ресурсов: " + bestMatch.getName());
            } else {
                // Берем самый новый
                File newest = getNewestApi(filesArray);
                if (newest != null) {
                    deps.add(newest);
                    System.out.println("📦 Используем самый новый API из ресурсов: " + newest.getName());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка при доступе к ресурсам JAR: " + e.getMessage());
            e.printStackTrace();
        }

        return deps;
    }

    // Получаем API файлы из ресурсов JAR
    private Map<String, File> getApiFilesFromResources() throws Exception {
        // Если уже кэшировали - возвращаем из кэша
        if (!cachedApiFiles.isEmpty()) {
            return cachedApiFiles;
        }

        // Создаем временную директорию для извлечения ресурсов
        if (tempExtractDir == null) {
            tempExtractDir = Files.createTempDirectory("bytecraft_libs_").toFile();
            tempExtractDir.deleteOnExit();
            System.out.println("📁 Временная директория для ресурсов: " + tempExtractDir.getAbsolutePath());
        }

        // Получаем ClassLoader
        ClassLoader classLoader = getClass().getClassLoader();

        try {
            // Пробуем получить ресурсы из JAR
            Enumeration<URL> resources = classLoader.getResources("libs");

            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                System.out.println("🔗 Найден ресурс: " + resourceUrl);

                if (resourceUrl.getProtocol().equals("jar")) {
                    // Это JAR файл, читаем его содержимое
                    String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
                    URL jarUrl = new URL(jarPath);

                    try (JarFile jarFile = new JarFile(new File(jarUrl.toURI()))) {
                        Enumeration<JarEntry> entries = jarFile.entries();

                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String entryName = entry.getName();

                            // Ищем файлы в папке libs
                            if (entryName.startsWith("libs/") &&
                                    entryName.endsWith(".jar") &&
                                    (entryName.toLowerCase().contains("spigot") ||
                                            entryName.toLowerCase().contains("bukkit") ||
                                            entryName.toLowerCase().contains("api"))) {

                                // Извлекаем файл
                                String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                                File extractedFile = new File(tempExtractDir, fileName);

                                if (!extractedFile.exists()) {
                                    try (InputStream is = jarFile.getInputStream(entry);
                                         OutputStream os = new FileOutputStream(extractedFile)) {
                                        is.transferTo(os);
                                    }
                                    extractedFile.deleteOnExit();
                                }

                                cachedApiFiles.put(fileName, extractedFile);
                                System.out.println("   📄 Извлечен ресурс: " + fileName);
                            }
                        }
                    }
                } else if (resourceUrl.getProtocol().equals("file")) {
                    // Это файловая система (для разработки)
                    File libsDir = new File(resourceUrl.toURI());
                    if (libsDir.exists() && libsDir.isDirectory()) {
                        File[] files = libsDir.listFiles((dir, name) ->
                                name.endsWith(".jar") && (
                                        name.toLowerCase().contains("spigot") ||
                                                name.toLowerCase().contains("bukkit") ||
                                                name.toLowerCase().contains("api")
                                )
                        );

                        if (files != null) {
                            for (File file : files) {
                                cachedApiFiles.put(file.getName(), file);
                                System.out.println("   📄 Найден файл: " + file.getAbsolutePath());
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️  Не удалось получить ресурсы: " + e.getMessage());

            // Fallback: проверяем внешнюю папку libs
            checkExternalLibsFolder();
        }

        // Если ничего не нашли в ресурсах, пробуем внешнюю папку
        if (cachedApiFiles.isEmpty()) {
            checkExternalLibsFolder();
        }

        return cachedApiFiles;
    }

    private void checkExternalLibsFolder() {
        // Проверяем несколько возможных путей
        String[] possiblePaths = {
                "libs",
                System.getProperty("user.dir") + "/libs",
                System.getProperty("user.home") + "/IdeaProjects/bytecraft/libs",
                "C:/Users/Gomer/IdeaProjects/bytecraft/libs"
        };

        for (String path : possiblePaths) {
            File libsFolder = new File(path);
            if (libsFolder.exists() && libsFolder.isDirectory()) {
                System.out.println("🔍 Проверяем внешнюю папку: " + libsFolder.getAbsolutePath());

                File[] files = libsFolder.listFiles((dir, name) ->
                        name.endsWith(".jar") && (
                                name.toLowerCase().contains("spigot") ||
                                        name.toLowerCase().contains("bukkit") ||
                                        name.toLowerCase().contains("api")
                        )
                );

                if (files != null && files.length > 0) {
                    for (File file : files) {
                        cachedApiFiles.put(file.getName(), file);
                        System.out.println("   📄 Найден внешний файл: " + file.getName());
                    }
                    break;
                }
            }
        }
    }

    private void addJavaLibraries(List<File> classpath) {
        String javaHome = System.getProperty("java.home");
        File libDir = new File(javaHome, "lib");

        if (libDir.exists()) {
            File[] javaLibs = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (javaLibs != null) {
                classpath.addAll(Arrays.asList(javaLibs));
            }
        }
    }

    // ... остальные методы без изменений ...

    private File getNewestApi(File[] apiFiles) {
        if (apiFiles == null || apiFiles.length == 0) {
            return null;
        }

        // Сортируем по версии (новые сначала)
        Arrays.sort(apiFiles, (a, b) -> {
            VersionUtils.McVersion v1 = VersionUtils.parseVersionFromFileName(a.getName());
            VersionUtils.McVersion v2 = VersionUtils.parseVersionFromFileName(b.getName());

            if (v1 == null && v2 == null) return 0;
            if (v1 == null) return 1;
            if (v2 == null) return -1;

            return v2.compareTo(v1); // По убыванию
        });

        return apiFiles[0];
    }

    private List<File> getMavenDependencies() {
        List<File> dependencies = new ArrayList<>();

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl instanceof URLClassLoader) {
                URLClassLoader ucl = (URLClassLoader) cl;
                for (URL url : ucl.getURLs()) {
                    if (url.getProtocol().equals("file")) {
                        try {
                            File file = new File(url.toURI());
                            if (file.exists() && file.getName().endsWith(".jar")) {
                                if (!dependencies.contains(file)) {
                                    dependencies.add(file);
                                }
                            }
                        } catch (Exception e) {
                            // Игнорируем
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️  Не удалось получить Maven зависимости: " + e.getMessage());
        }

        return dependencies;
    }

    public byte[] compileJavaSource(String javaCode, String className, List<File> classpath, File originalJar) throws Exception {
        // Создаем временный .java файл
        Path tempDir = Files.createTempDirectory("compile_source_");
        Path javaFile = tempDir.resolve(className + ".java");

        try {
            Files.writeString(javaFile, javaCode);
            System.out.println("📝 Размер кода для компиляции: " + javaCode.length() + " символов");
            System.out.println("🔧 Компиляция класса: " + className);

            return compileJavaFile(javaFile.toFile(), classpath, originalJar);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // Старый метод для обратной совместимости (нужен для JarPatcher)
    public byte[] compileJavaSource(String javaCode, String className, List<File> classpath) throws Exception {
        return compileJavaSource(javaCode, className, classpath, null);
    }

    private Path findClassFile(Path dir, String className) throws IOException {
        try (var stream = Files.walk(dir)) {
            return stream
                    .filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.equals(className + ".class") ||
                                name.startsWith(className + "$");
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    public boolean isCompilerAvailable() {
        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("❌ Java компилятор не найден!");
            System.err.println("   Текущая Java: " + System.getProperty("java.version"));
            System.err.println("   JAVA_HOME: " + System.getProperty("java.home"));
            return false;
        }
        return true;
    }

    public String getCompilerInfo() {
        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return "❌ Компилятор не найден (нужен JDK)";
        }

        String javaVersion = System.getProperty("java.version");
        String javaHome = System.getProperty("java.home");

        return "✅ Компилятор доступен: Java " + javaVersion + " (" + javaHome + ")";
    }

    private void deleteDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (Exception e) {
            System.err.println("⚠️  Ошибка удаления директории: " + e.getMessage());
        }
    }

    public static class CompilationException extends Exception {
        public CompilationException(String message) {
            super(message);
        }
    }
}