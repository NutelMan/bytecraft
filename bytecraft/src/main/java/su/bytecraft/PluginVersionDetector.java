package su.bytecraft;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.jar.*;

public class PluginVersionDetector {

    public static VersionUtils.McVersion detectMcVersion(File jarFile) {
        System.out.println("🔍 Определение версии Minecraft из плагина: " + jarFile.getName());

        // Сначала пробуем прочитать plugin.yml
        String ymlVersion = detectFromPluginYml(jarFile);
        if (ymlVersion != null) {
            System.out.println("📄 Версия из plugin.yml: " + ymlVersion);
            return new VersionUtils.McVersion(ymlVersion);
        }

        // Пробуем paper-plugin.yml
        String paperVersion = detectFromPaperPluginYml(jarFile);
        if (paperVersion != null) {
            System.out.println("📄 Версия из paper-plugin.yml: " + paperVersion);
            return new VersionUtils.McVersion(paperVersion);
        }

        // Пробуем bungee.yml для BungeeCord плагинов
        String bungeeVersion = detectFromBungeeYml(jarFile);
        if (bungeeVersion != null) {
            System.out.println("📄 Версия из bungee.yml: " + bungeeVersion);
            return new VersionUtils.McVersion(bungeeVersion);
        }

        // Анализируем классы
        String classVersion = detectFromClasses(jarFile);
        if (classVersion != null) {
            System.out.println("⚙️  Версия из анализа классов: " + classVersion);
            return new VersionUtils.McVersion(classVersion);
        }

        // Версия по умолчанию
        System.out.println("⚠️  Не удалось определить версию, используем 1.20 по умолчанию");
        return new VersionUtils.McVersion("1.20");
    }

    private static String detectFromPluginYml(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                return null;
            }

            try (InputStream is = jar.getInputStream(pluginYml)) {
                String content = new String(is.readAllBytes());
                return parseVersionFromYml(content);
            }
        } catch (Exception e) {
            System.err.println("⚠️  Ошибка чтения plugin.yml: " + e.getMessage());
            return null;
        }
    }

    private static String detectFromPaperPluginYml(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry paperYml = jar.getJarEntry("paper-plugin.yml");
            if (paperYml == null) {
                return null;
            }

            try (InputStream is = jar.getInputStream(paperYml)) {
                String content = new String(is.readAllBytes());
                return parseVersionFromYml(content);
            }
        } catch (Exception e) {
            System.err.println("⚠️  Ошибка чтения paper-plugin.yml: " + e.getMessage());
            return null;
        }
    }

    private static String detectFromBungeeYml(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry bungeeYml = jar.getJarEntry("bungee.yml");
            if (bungeeYml == null) {
                return null;
            }

            try (InputStream is = jar.getInputStream(bungeeYml)) {
                String content = new String(is.readAllBytes());

                // Для BungeeCord ищем версию
                Pattern pattern = Pattern.compile("version:\\s*[\"']?([0-9.]+)[\"']?");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return normalizeVersion(matcher.group(1));
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return null;
    }

    private static String parseVersionFromYml(String ymlContent) {
        // Список паттернов для поиска версии (в порядке приоритета)
        String[] patterns = {
                "api-version:\\s*[\"']?(1\\.[0-9]{1,2}(\\.[0-9]{1,2})?)[\"']?",
                "mc-version:\\s*[\"']?(1\\.[0-9]{1,2}(\\.[0-9]{1,2})?)[\"']?",
                "minecraft:\\s*[\"']?(1\\.[0-9]{1,2}(\\.[0-9]{1,2})?)[\"']?",
                "server-version:\\s*[\"']?(1\\.[0-9]{1,2}(\\.[0-9]{1,2})?)[\"']?",
                "version:\\s*[\"']?(1\\.[0-9]{1,2}(\\.[0-9]{1,2})?)[\"']?",
                "\\b(1\\.[0-9]{1,2}(\\.[0-9]{1,2})?)\\b"  // Любая версия в тексте
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(ymlContent);
            while (m.find()) {
                String version = m.group(1);
                if (version != null && version.matches("1\\.[0-9]{1,2}(\\.[0-9]{1,2})?")) {
                    String normalized = normalizeVersion(version);
                    if (normalized != null) {
                        return normalized;
                    }
                }
            }
        }

        return null;
    }

    private static String detectFromClasses(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            // Ищем версии в именах пакетов и классов
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    // Проверяем версию в имени класса
                    String className = name.replace("/", ".").replace(".class", "");

                    // Ищем паттерны версий
                    Pattern versionPattern = Pattern.compile("v?(1_[0-9]{1,2}_[R0-9]*)");
                    Matcher matcher = versionPattern.matcher(className);

                    if (matcher.find()) {
                        String versionCode = matcher.group(1);
                        return convertVersionCode(versionCode);
                    }

                    // Ищем цифровые версии
                    Pattern digitPattern = Pattern.compile("\\b(1\\.[0-9]{1,2}(\\.[0-9]{1,2})?)\\b");
                    matcher = digitPattern.matcher(className);

                    if (matcher.find()) {
                        return normalizeVersion(matcher.group(1));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️  Ошибка анализа классов: " + e.getMessage());
        }

        return null;
    }

    private static String convertVersionCode(String versionCode) {
        // Конвертируем v1_16_R3 -> 1.16.3
        try {
            versionCode = versionCode.replace("v", "").replace("_R", ".");
            String[] parts = versionCode.split("\\.");

            if (parts.length >= 2) {
                String major = parts[0]; // 1
                String minor = parts[1]; // 16

                if (parts.length >= 3) {
                    return major + "." + minor + "." + parts[2]; // 1.16.3
                } else {
                    return major + "." + minor; // 1.16
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки конвертации
        }

        return null;
    }

    private static String normalizeVersion(String version) {
        if (version == null) return null;

        // Убираем лишние символы
        version = version.trim()
                .replace("\"", "")
                .replace("'", "")
                .replace("v", "")
                .replace("V", "");

        // Проверяем формат
        if (!version.matches("1\\.[0-9]{1,2}(\\.[0-9]{1,2})?")) {
            return null;
        }

        // Приводим к стандартному виду
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            // Если есть патч-версия
            if (parts.length >= 3) {
                return parts[0] + "." + parts[1] + "." + parts[2];
            } else {
                return parts[0] + "." + parts[1];
            }
        }

        return null;
    }

    // Дополнительный метод для получения информации о плагине
    public static PluginInfo getPluginInfo(File jarFile) {
        PluginInfo info = new PluginInfo();

        try (JarFile jar = new JarFile(jarFile)) {
            // Читаем plugin.yml
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml != null) {
                try (InputStream is = jar.getInputStream(pluginYml)) {
                    String content = new String(is.readAllBytes());
                    parsePluginInfo(content, info);
                }
            }

            // Устанавливаем версию MC
            info.mcVersion = detectMcVersion(jarFile);

        } catch (Exception e) {
            System.err.println("⚠️  Ошибка получения информации о плагине: " + e.getMessage());
        }

        return info;
    }

    private static void parsePluginInfo(String ymlContent, PluginInfo info) {
        // Парсим основные поля
        String[] fields = {"name", "version", "main", "author", "authors", "description", "website"};

        for (String field : fields) {
            Pattern pattern = Pattern.compile(field + ":\\s*[\"']?(.*?)[\"']?\\s*$", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(ymlContent);
            if (matcher.find()) {
                String value = matcher.group(1).trim();

                switch (field) {
                    case "name":
                        info.name = value;
                        break;
                    case "version":
                        info.pluginVersion = value;
                        break;
                    case "main":
                        info.mainClass = value;
                        break;
                    case "author":
                        info.author = value;
                        break;
                    case "authors":
                        info.authors = Arrays.asList(value.split(",\\s*"));
                        break;
                    case "description":
                        info.description = value;
                        break;
                    case "website":
                        info.website = value;
                        break;
                }
            }
        }
    }

    public static class PluginInfo {
        public String name;
        public String pluginVersion;
        public VersionUtils.McVersion mcVersion;
        public String mainClass;
        public String author;
        public List<String> authors;
        public String description;
        public String website;

        @Override
        public String toString() {
            return "PluginInfo{" +
                    "name='" + name + '\'' +
                    ", pluginVersion='" + pluginVersion + '\'' +
                    ", mcVersion=" + mcVersion +
                    ", mainClass='" + mainClass + '\'' +
                    '}';
        }
    }
}