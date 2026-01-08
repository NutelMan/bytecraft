package su.bytecraft;

import java.util.*;
import java.util.regex.*;
import java.io.File;

public class VersionUtils {

    // парсинг версии
    public static McVersion parseVersionFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        //в порядке приоритета
        Pattern[] patterns = {
                // spigot 1.21.9
                Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)-R\\d+\\.\\d+-SNAPSHOT", Pattern.CASE_INSENSITIVE),
                // spigot 1.21-R0.1
                Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)-R\\d+\\.\\d+-\\d{8}\\.\\d{6}-\\d+", Pattern.CASE_INSENSITIVE),
                // spigot 1.16.5
                Pattern.compile("[a-z-]+-(\\d+\\.\\d+(\\.\\d+)?)[^\\d]", Pattern.CASE_INSENSITIVE),
                // 1.20.1
                Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)\\.jar", Pattern.CASE_INSENSITIVE),
                // любая версия  X.Y.Z
                Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.find()) {
                String versionStr = matcher.group(1);
                try {
                    return new McVersion(versionStr);
                } catch (Exception e) {
                    continue; // Пробуем следующий паттерн
                }
            }
        }

        return null;
    }

    // Класс для представления версии
    public static class McVersion implements Comparable<McVersion> {
        private final int major;
        private final int minor;
        private final int patch;
        private final String original;

        public McVersion(String version) {
            if (version == null) {
                throw new IllegalArgumentException("Version cannot be null");
            }

            this.original = version.trim();
            String[] parts = this.original.split("\\.");

            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid version format: " + version);
            }

            this.major = parseInt(parts[0]);
            this.minor = parseInt(parts[1]);
            this.patch = parts.length > 2 ? parseInt(parts[2]) : 0;
        }

        private int parseInt(String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Проверяем, совместима ли эта версия с целевой
        public boolean isCompatibleWith(McVersion target) {
            // Для Minecraft API:
            // - Мажорная версия всегда 1
            // - Минорная версия определяет совместимость
            // - Патч обычно совместим

            if (this.major != target.major) {
                return false; // Всегда должна быть 1
            }

            // Проверяем совместимость минорных версий
            int diff = Math.abs(this.minor - target.minor);

            // Версии отличаются на 1-2 обычно совместимы
            return diff <= 2;
        }

        // Насколько близка эта версия к целевой
        public int distanceTo(McVersion target) {
            if (this.major != target.major) {
                return Math.abs(this.major - target.major) * 10000;
            }
            if (this.minor != target.minor) {
                return Math.abs(this.minor - target.minor) * 100;
            }
            return Math.abs(this.patch - target.patch);
        }

        // Приоритет: более новая версия лучше
        public int priorityScore(McVersion target) {
            int distance = distanceTo(target);
            int agePenalty = (target.minor - this.minor) * 10; // Старые версии получают штраф

            return distance - agePenalty;
        }

        @Override
        public int compareTo(McVersion other) {
            if (this.major != other.major) return Integer.compare(this.major, other.major);
            if (this.minor != other.minor) return Integer.compare(this.minor, other.minor);
            return Integer.compare(this.patch, other.patch);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            McVersion that = (McVersion) obj;
            return major == that.major && minor == that.minor && patch == that.patch;
        }

        @Override
        public int hashCode() {
            return Objects.hash(major, minor, patch);
        }

        @Override
        public String toString() {
            if (patch > 0) {
                return major + "." + minor + "." + patch;
            } else {
                return major + "." + minor;
            }
        }

        public String getOriginal() {
            return original;
        }

        public int getMajor() { return major; }
        public int getMinor() { return minor; }
        public int getPatch() { return patch; }

        public boolean isGreaterThan(McVersion other) {
            return this.compareTo(other) > 0;
        }

        public boolean isGreaterOrEqual(McVersion other) {
            return this.compareTo(other) >= 0;
        }

        public boolean isLessThan(McVersion other) {
            return this.compareTo(other) < 0;
        }

        public boolean isLessOrEqual(McVersion other) {
            return this.compareTo(other) <= 0;
        }
    }

    // Находим наиболее подходящую версию
    public static File findBestMatchVersion(File[] apiFiles, McVersion targetVersion) {
        if (apiFiles == null || apiFiles.length == 0 || targetVersion == null) {
            return null;
        }

        List<ApiCandidate> candidates = new ArrayList<>();

        // Парсим все файлы и создаем кандидатов
        for (File file : apiFiles) {
            McVersion version = parseVersionFromFileName(file.getName());
            if (version != null) {
                candidates.add(new ApiCandidate(file, version));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Сортируем кандидатов по приоритету
        candidates.sort((a, b) -> {
            // Сначала точные совпадения
            boolean aExact = a.version.equals(targetVersion);
            boolean bExact = b.version.equals(targetVersion);

            if (aExact && !bExact) return -1;
            if (!aExact && bExact) return 1;

            // Затем совместимые версии
            boolean aCompatible = a.version.isCompatibleWith(targetVersion);
            boolean bCompatible = b.version.isCompatibleWith(targetVersion);

            if (aCompatible && !bCompatible) return -1;
            if (!aCompatible && bCompatible) return 1;

            // Обе совместимы или обе нет - считаем приоритет
            int priorityA = a.version.priorityScore(targetVersion);
            int priorityB = b.version.priorityScore(targetVersion);

            if (priorityA != priorityB) {
                return Integer.compare(priorityA, priorityB); // Меньший приоритет лучше
            }

            // Если приоритеты равны - берем более новую
            return b.version.compareTo(a.version);
        });

        ApiCandidate best = candidates.get(0);

        // Логируем выбор
        System.out.println("🎯 Выбор API для версии " + targetVersion + ":");
        System.out.println("   ✅ Лучший кандидат: " + best.file.getName() + " (версия " + best.version + ")");

        if (candidates.size() > 1) {
            System.out.println("   📊 Альтернативы:");
            for (int i = 1; i < Math.min(4, candidates.size()); i++) {
                ApiCandidate alt = candidates.get(i);
                System.out.println("      • " + alt.file.getName() + " (версия " + alt.version +
                        ", приоритет: " + alt.version.priorityScore(targetVersion) + ")");
            }
        }

        return best.file;
    }

    private static class ApiCandidate {
        File file;
        McVersion version;

        ApiCandidate(File file, McVersion version) {
            this.file = file;
            this.version = version;
        }
    }
}