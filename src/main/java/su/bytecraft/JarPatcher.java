package su.bytecraft;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.*;

public class JarPatcher {

    private JavaCompiler compiler;

    public JarPatcher() {
        this.compiler = new JavaCompiler();
    }

    public File createPatchedJarFromModifiedClass(File originalJar, String className,
                                                  String modifiedJavaCode) throws Exception {
        // Создаем временный файл для пропатченного JAR
        Path tempJar = Files.createTempFile("patched", ".jar");

        try (JarInputStream jis = new JarInputStream(new FileInputStream(originalJar));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar.toFile()))) {

            // Получаем classpath
            List<File> classpath = getClasspathFromJar(originalJar);


            System.out.println("📦 Начинаем патчинг JAR: " + originalJar.getName());
            System.out.println("🔧 Класс для патча: " + className);

            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                byte[] entryData;

                // Проверяем, это ли нужный нам класс
                String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
                String expectedClassPath = className.replace('.', '/') + ".class";

                if (entryName.equals(expectedClassPath)) {
                    System.out.println("⚡ Найден класс для компиляции: " + entryName);

                    // Компилируем измененный Java код
                    try {
                        entryData = compiler.compileJavaSource(modifiedJavaCode, simpleClassName, classpath);
                        System.out.println("✅ Успешно скомпилирован: " + className);
                    } catch (JavaCompiler.CompilationException e) {
                        System.err.println("❌ Ошибка компиляции:");
                        System.err.println(e.getMessage());
                        throw e;
                    } catch (Exception e) {
                        System.err.println("❌ Неожиданная ошибка компиляции:");
                        e.printStackTrace();
                        throw new RuntimeException("Ошибка компиляции " + className + ": " + e.getMessage(), e);
                    }
                } else {
                    // Копируем как есть
                    entryData = jis.readAllBytes();
                }

                JarEntry newEntry = new JarEntry(entryName);
                jos.putNextEntry(newEntry);
                jos.write(entryData);
                jos.closeEntry();
                jis.closeEntry();
            }
        } catch (Exception e) {
            Files.deleteIfExists(tempJar);
            throw e;
        }

        // Создаем пропатченный файл рядом с оригиналом
        String newName = originalJar.getName().replace(".jar", "_PATCHED.jar");
        File patchedJar = new File(originalJar.getParent(), newName);

        System.out.println("💾 Сохраняем пропатченный JAR как: " + patchedJar.getName());

        Files.copy(tempJar, patchedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(tempJar);

        System.out.println("✅ Пропатченный JAR создан: " + patchedJar.getAbsolutePath());
        System.out.println("📊 Размер: " + patchedJar.length() + " байт");

        return patchedJar;
    }

    private List<File> getClasspathFromJar(File jarFile) {
        List<File> classpath = new ArrayList<>();
        classpath.add(jarFile);

        System.out.println("🔍 Поиск зависимостей для компиляции...");

        // Добавляем стандартные библиотеки
        String javaHome = System.getProperty("java.home");
        File rtJar = new File(javaHome, "lib/rt.jar");
        if (rtJar.exists()) {
            classpath.add(rtJar);
            System.out.println("   ✅ Добавлен rt.jar");
        }

        // Добавляем библиотеки из папки с плагином
        File pluginDir = jarFile.getParentFile();
        if (pluginDir != null && pluginDir.exists()) {
            File[] libs = pluginDir.listFiles((dir, name) ->
                    name.endsWith(".jar") &&
                            !name.equals(jarFile.getName()) &&
                            !name.contains("_PATCHED") &&        // Исключение патчей
                            !name.contains("_STRING_PATCHED")
            );
            if (libs != null) {
                for (File lib : libs) {
                    classpath.add(lib);
                    System.out.println("   ✅ Добавлена зависимость: " + lib.getName());
                }
            }
        }

        // Добавляем Spigot API из папки libs в проекте
        File libsFolder = new File("libs");
        if (libsFolder.exists() && libsFolder.isDirectory()) {
            File[] spigotLibs = libsFolder.listFiles((dir, name) ->
                    name.toLowerCase().contains("spigot") ||
                            name.toLowerCase().contains("bukkit") ||
                            name.toLowerCase().contains("server"));
            if (spigotLibs != null) {
                for (File lib : spigotLibs) {
                    classpath.add(lib);
                    System.out.println("   ✅ Добавлен Spigot API: " + lib.getName());
                }
            }
        }

        return classpath;
    }

    // Простая замена строк в байткоде - альт метод
    public File createPatchedJarWithStringReplacement(File originalJar, String oldString, String newString) throws Exception {
        Path tempJar = Files.createTempFile("patched_string", ".jar");

        try (JarInputStream jis = new JarInputStream(new FileInputStream(originalJar));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar.toFile()))) {

            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                byte[] entryData;

                if (entry.getName().endsWith(".class")) {
                    entryData = replaceStringInClass(jis.readAllBytes(), oldString, newString);
                } else {
                    entryData = jis.readAllBytes();
                }

                JarEntry newEntry = new JarEntry(entry.getName());
                jos.putNextEntry(newEntry);
                jos.write(entryData);
                jos.closeEntry();
                jis.closeEntry();
            }
        }

        String newName = originalJar.getName().replace(".jar", "_STRING_PATCHED.jar");
        File patchedJar = new File(originalJar.getParent(), newName);
        Files.copy(tempJar, patchedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(tempJar);

        return patchedJar;
    }

    private byte[] replaceStringInClass(byte[] classBytes, String oldString, String newString) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String) {
                            String str = (String) value;
                            // Заменяем строку если она полностью совпадает
                            if (str.equals(oldString)) {
                                super.visitLdcInsn(newString);
                                return;
                            }
                            // Или заменяем часть строки
                            if (str.contains(oldString)) {
                                super.visitLdcInsn(str.replace(oldString, newString));
                                return;
                            }
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private void log(String message) {
        System.out.println("[JarPatcher] " + message);
    }
}