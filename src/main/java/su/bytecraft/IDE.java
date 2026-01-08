package su.bytecraft;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IDE extends Application {

    private Stage primaryStage;
    private BorderPane root;
    private TextArea codeArea;
    private TreeView<String> fileTree;
    private Label statusLabel;
    private ProgressBar progressBar;

    private Decompiler decompiler;
    private JarPatcher patcher;
    private JavaCompiler javaCompiler;
    private File currentJar;
    private String currentClassName;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Override
    public void start(Stage primaryStage) {
        // Устанавливаем глобальный обработчик необработанных исключений
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("⚠️ НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ в потоке " + thread.getName() + ":");
            throwable.printStackTrace();

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Необработанная ошибка");
                alert.setHeaderText("Произошла ошибка в потоке: " + thread.getName());

                TextArea textArea = new TextArea(getStackTrace(throwable));
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);

                alert.getDialogPane().setExpandableContent(new ScrollPane(textArea));
                alert.getDialogPane().setExpanded(true);
                alert.setContentText(throwable.getMessage());
                alert.showAndWait();
            });
        });

        this.primaryStage = primaryStage;

        try {
            System.out.println("🔄 Инициализация компонентов...");
            decompiler = new Decompiler();
            patcher = new JarPatcher();
            javaCompiler = new JavaCompiler();

            System.out.println("✅ Компоненты инициализированы");

            setupStage();
            createUI();

            primaryStage.show();
            updateStatus("Готов");
            checkCompiler();

            System.out.println("✅ ByteCraft успешно запущен");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при запуске IDE:");
            e.printStackTrace();
            showCriticalError("Ошибка запуска", e);
        }
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private void showCriticalError(String title, Throwable throwable) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText("Критическая ошибка");

            TextArea textArea = new TextArea(getStackTrace(throwable));
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefSize(600, 400);

            VBox content = new VBox(10,
                    new Label("Сообщение: " + throwable.getMessage()),
                    new Label("Трассировка:"),
                    textArea
            );
            content.setPadding(new Insets(10));

            alert.getDialogPane().setContent(content);
            alert.showAndWait();

            Platform.exit();
        });
    }

    private void setupStage() {
        primaryStage.setTitle("ByteCraft - Декомпилятор и Репатчер");
        primaryStage.setWidth(1200);
        primaryStage.setHeight(800);
    }

    private void createUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");

        root.setTop(createToolbar());
        root.setLeft(createSidebar());
        root.setCenter(createEditorArea());
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);

        // Обработчик ошибок в JavaFX потоке
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("👋 Закрытие приложения...");
            executor.shutdown();
        });
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #252526;");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button openBtn = createToolbarButton("📁 Открыть плагин", this::openPlugin);
        Button decompileBtn = createToolbarButton("🔧 Декомпилировать", this::decompileSelected);
        Button patchBtn = createToolbarButton("⚡ Собрать патч", this::compileAndPack);

        toolbar.getChildren().addAll(openBtn, decompileBtn, patchBtn);
        return toolbar;
    }

    private Button createToolbarButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #007acc; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 13px; " +
                "-fx-padding: 8 15; " +
                "-fx-cursor: hand;");

        btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: #0088dd; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 13px; " +
                        "-fx-padding: 8 15; " +
                        "-fx-cursor: hand;"));

        btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: #007acc; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 13px; " +
                        "-fx-padding: 8 15; " +
                        "-fx-cursor: hand;"));

        btn.setOnAction(e -> {
            try {
                action.run();
            } catch (Exception ex) {
                System.err.println("❌ Ошибка в обработчике кнопки " + text + ":");
                ex.printStackTrace();
                showError("Ошибка", ex.getMessage());
            }
        });
        return btn;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(250);
        sidebar.setStyle("-fx-background-color: #252526;");
        sidebar.setPadding(new Insets(10));

        Label title = new Label("Классы плагина");
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold;");
        title.setPadding(new Insets(0, 0, 10, 0));

        fileTree = new TreeView<>();
        fileTree.setStyle("-fx-background-color: transparent;");

        TreeItem<String> rootItem = new TreeItem<>("root");
        rootItem.setExpanded(true);
        fileTree.setRoot(rootItem);
        fileTree.setShowRoot(false);

        fileTree.setOnMouseClicked(e -> {
            try {
                if (e.getClickCount() == 2) {
                    TreeItem<String> item = fileTree.getSelectionModel().getSelectedItem();
                    if (item != null && item.isLeaf()) {
                        decompileClass(item.getValue());
                    }
                }
            } catch (Exception ex) {
                System.err.println("❌ Ошибка при двойном клике:");
                ex.printStackTrace();
                showError("Ошибка", ex.getMessage());
            }
        });

        sidebar.getChildren().addAll(title, fileTree);
        VBox.setVgrow(fileTree, Priority.ALWAYS);

        return sidebar;
    }

    private ScrollPane createEditorArea() {
        codeArea = new TextArea();
        codeArea.setStyle("-fx-font-family: 'Consolas', monospace; " +
                "-fx-font-size: 13px; " +
                "-fx-control-inner-background: #1e1e1e; " +
                "-fx-text-fill: #cccccc;");
        codeArea.setEditable(true);

        ScrollPane scrollPane = new ScrollPane(codeArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        return scrollPane;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #007acc;");

        statusLabel = new Label("Готов");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setPrefWidth(150);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label compilerLabel = new Label("");
        compilerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");

        statusBar.getChildren().addAll(statusLabel, spacer, progressBar, compilerLabel);
        return statusBar;
    }

    private void checkCompiler() {
        System.out.println("🔍 Проверка наличия компилятора...");
        try {
            if (!javaCompiler.isCompilerAvailable()) {
                System.err.println("⚠️ Компилятор не найден!");
                showWarning("Внимание",
                        "Java компилятор не найден!\n" +
                                "Для сборки патчей нужен JDK.\n" +
                                "Скачайте и установите JDK с https://adoptium.net/\n" +
                                "Текущая Java: " + System.getProperty("java.version"));
            } else {
                System.out.println("✅ Компилятор доступен");
            }

            // Обновляем информацию в статус баре
            Label compilerLabel = (Label) ((HBox) root.getBottom()).getChildren().get(3);
            compilerLabel.setText(javaCompiler.getCompilerInfo());

        } catch (Exception e) {
            System.err.println("❌ Ошибка при проверке компилятора:");
            e.printStackTrace();
        }
    }

    private void openPlugin() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите плагин (.jar)");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JAR файлы", "*.jar")
            );

            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                currentJar = file;
                updateStatus("Открыт: " + file.getName());
                System.out.println("📦 Открыт файл: " + file.getAbsolutePath());
                loadClassTree();
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка при открытии плагина:");
            e.printStackTrace();
            showError("Ошибка открытия", e.getMessage());
        }
    }

    private void loadClassTree() {
        if (currentJar == null) return;

        showProgress(true);

        executor.submit(() -> {
            try {
                System.out.println("🌳 Загрузка дерева классов из: " + currentJar.getName());
                List<String> classes = decompiler.getClassesFromJar(currentJar);
                System.out.println("📊 Найдено классов: " + classes.size());

                Platform.runLater(() -> {
                    TreeItem<String> root = new TreeItem<>("Классы");
                    root.setExpanded(true);

                    Map<String, TreeItem<String>> packages = new HashMap<>();

                    for (String className : classes) {
                        String[] parts = className.split("/");
                        TreeItem<String> parent = root;

                        for (int i = 0; i < parts.length - 1; i++) {
                            String packageName = parts[i];
                            String fullPath = String.join("/", Arrays.copyOfRange(parts, 0, i + 1));

                            if (!packages.containsKey(fullPath)) {
                                TreeItem<String> packageItem = new TreeItem<>(packageName);
                                parent.getChildren().add(packageItem);
                                packages.put(fullPath, packageItem);
                                parent = packageItem;
                            } else {
                                parent = packages.get(fullPath);
                            }
                        }

                        TreeItem<String> classItem = new TreeItem<>(parts[parts.length - 1]);
                        classItem.setValue(className);
                        parent.getChildren().add(classItem);
                    }

                    fileTree.setRoot(root);
                    showProgress(false);
                    updateStatus("✅ Загружено классов: " + classes.size());
                    System.out.println("✅ Дерево классов построено");
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка при загрузке дерева классов:");
                e.printStackTrace();
                Platform.runLater(() -> {
                    showProgress(false);
                    showError("Ошибка загрузки", e.getMessage());
                });
            }
        });
    }

    private void decompileSelected() {
        try {
            TreeItem<String> selected = fileTree.getSelectionModel().getSelectedItem();
            if (selected == null || !selected.isLeaf()) {
                showWarning("Выберите класс", "Дважды кликните на классе в списке слева");
                return;
            }

            decompileClass(selected.getValue());
        } catch (Exception e) {
            System.err.println("❌ Ошибка при выборе класса:");
            e.printStackTrace();
            showError("Ошибка", e.getMessage());
        }
    }

    private void decompileClass(String className) {
        if (currentJar == null) {
            showWarning("Нет плагина", "Сначала откройте плагин");
            return;
        }

        currentClassName = className.replace(".class", "").replace("/", ".");
        updateStatus("Декомпиляция...");
        showProgress(true);

        executor.submit(() -> {
            try {
                System.out.println("🔧 Декомпиляция класса: " + className);
                String code = decompiler.decompileClassFromJar(currentJar, className);
                System.out.println("✅ Класс декомпилирован, размер кода: " + code.length() + " символов");

                Platform.runLater(() -> {
                    codeArea.setText(code);
                    showProgress(false);
                    updateStatus("✅ Декомпилирован: " + currentClassName);
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка при декомпиляции класса " + className + ":");
                e.printStackTrace();
                Platform.runLater(() -> {
                    showProgress(false);
                    showError("Ошибка декомпиляции", e.getMessage());
                });
            }
        });
    }

    private void compileAndPack() {
        if (currentJar == null) {
            showWarning("Нет плагина", "Сначала откройте плагин");
            return;
        }

        if (currentClassName == null) {
            showWarning("Нет класса", "Сначала декомпилируйте класс");
            return;
        }

        String modifiedCode = codeArea.getText();
        if (modifiedCode.isEmpty()) {
            showWarning("Нет кода", "Нет кода для компиляции");
            return;
        }

        if (!javaCompiler.isCompilerAvailable()) {
            showError("Нет компилятора",
                    "JDK не найден!\n" +
                            "Скачайте и установите JDK:\n" +
                            "https://adoptium.net/\n\n" +
                            "После установки перезапустите программу.");
            return;
        }

        // Подтверждение
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Подтверждение");
        confirm.setHeaderText("Собрать патч?");
        confirm.setContentText(
                "Класс: " + currentClassName + "\n" +
                        "Будет создан новый JAR файл рядом с оригиналом."
        );

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        updateStatus("Компиляция и упаковка...");
        showProgress(true);

        executor.submit(() -> {
            try {
                System.out.println("⚡ Начало компиляции класса: " + currentClassName);
                System.out.println("📝 Размер кода: " + modifiedCode.length() + " символов");

                // Создаем пропатченный JAR
                File patchedJar = patcher.createPatchedJarFromModifiedClass(
                        currentJar, currentClassName, modifiedCode
                );

                System.out.println("✅ Пропатченный JAR создан: " + patchedJar.getAbsolutePath());
                System.out.println("📁 Размер нового JAR: " + patchedJar.length() + " байт");

                Platform.runLater(() -> {
                    showProgress(false);
                    updateStatus("✅ Готово");

                    // Открываем папку с пропатченным файлом
                    try {
                        java.awt.Desktop.getDesktop().open(patchedJar.getParentFile());
                        System.out.println("📂 Папка открыта в проводнике");
                        showInfo("Успех",
                                "✅ Пропатченный плагин создан!\n" +
                                        "📁 Файл: " + patchedJar.getName() + "\n" +
                                        "📍 Папка открыта в проводнике.");
                    } catch (Exception e) {
                        System.out.println("ℹ️ Не удалось открыть папку: " + e.getMessage());
                        showInfo("Успех",
                                "✅ Пропатченный плагин создан!\n" +
                                        "📁 Файл: " + patchedJar.getName() + "\n" +
                                        "📍 Путь: " + patchedJar.getAbsolutePath());
                    }
                });
            } catch (JavaCompiler.CompilationException e) {
                System.err.println("❌ Ошибка компиляции:");
                e.printStackTrace();
                Platform.runLater(() -> {
                    showProgress(false);
                    showError("❌ Ошибка компиляции", e.getMessage());
                });
            } catch (Exception e) {
                System.err.println("❌ Общая ошибка при сборке патча:");
                e.printStackTrace();
                Platform.runLater(() -> {
                    showProgress(false);
                    showError("❌ Ошибка",
                            "Ошибка: " + e.getMessage() + "\n\n" +
                                    "Проверьте консоль для деталей.");
                });
            }
        });
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            System.out.println("[STATUS] " + message);
        });
    }

    private void showProgress(boolean show) {
        Platform.runLater(() -> {
            progressBar.setVisible(show);
            progressBar.setProgress(show ? ProgressBar.INDETERMINATE_PROGRESS : 0);
        });
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            System.err.println("[ERROR] " + title + ": " + message);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showWarning(String title, String message) {
        Platform.runLater(() -> {
            System.out.println("[WARN] " + title + ": " + message);
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            System.out.println("[INFO] " + title + ": " + message);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() {
        System.out.println("🛑 Остановка приложения...");
        executor.shutdown();
        System.out.println("👋 ByteCraft завершил работу");
    }
}