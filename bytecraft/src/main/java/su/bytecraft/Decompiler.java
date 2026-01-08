package su.bytecraft;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Decompiler {

    public String decompileClassFromJar(File jarFile, String className) throws Exception {
        Path tempDir = Files.createTempDirectory("bytecraft_");

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.jar.JarEntry entry = jar.getJarEntry(className);
            if (entry == null) {
                throw new IOException("Класс не найден: " + className);
            }

            Path tempClass = tempDir.resolve(className);
            Files.createDirectories(tempClass.getParent());

            try (InputStream is = jar.getInputStream(entry);
                 OutputStream os = Files.newOutputStream(tempClass)) {
                is.transferTo(os);
            }

            return decompileWithCFR(tempClass.toFile());
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private String decompileWithCFR(File classFile) {
        try {
            List<String> result = new ArrayList<>();

            OutputSinkFactory sinkFactory = new OutputSinkFactory() {
                @Override
                public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                    return Arrays.asList(SinkClass.STRING, SinkClass.DECOMPILED);
                }

                @Override
                public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                    return data -> {
                        if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                            if (data instanceof SinkReturns.Decompiled) {
                                result.add(((SinkReturns.Decompiled) data).getJava());
                            }
                        } else if (sinkType == SinkType.JAVA && sinkClass == SinkClass.STRING) {
                            result.add(data.toString());
                        }
                    };
                }
            };

            Map<String, String> options = new HashMap<>();
            options.put("comments", "true");
            options.put("sugarenums", "true");
            options.put("decodeenumswitch", "true");

            CfrDriver driver = new CfrDriver.Builder()
                    .withOptions(options)
                    .withOutputSink(sinkFactory)
                    .build();

            driver.analyse(Collections.singletonList(classFile.getAbsolutePath()));

            if (!result.isEmpty()) {
                return String.join("\n", result);
            }
            return "// Нет результата";

        } catch (Exception e) {
            return "// Ошибка декомпиляции: " + e.getMessage();
        }
    }

    public List<String> getClassesFromJar(File jarFile) throws IOException {
        List<String> classes = new ArrayList<>();

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classes.add(entry.getName());
                }
            }
        }

        return classes;
    }

    private void deleteDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (Exception ignored) {}
    }
}