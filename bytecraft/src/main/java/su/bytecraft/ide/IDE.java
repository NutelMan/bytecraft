package su.bytecraft.ide;

import su.bytecraft.Decompiler;
import su.bytecraft.JarPatcher;
import su.bytecraft.JavaCompiler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class IDE extends Application {

    // Основные компоненты
    private Stage primaryStage;
    private BorderPane root;
    private TextArea codeArea;
    private TreeView<String> fileTree;
    private Label statusLabel;
    private ProgressBar progressBar;

    // Поисковые элементы
    private TextField searchField;
    private CheckBox caseSensitiveCheck;
    private CheckBox wholeWordCheck;
    private CheckBox regexCheck;
    private CheckBox searchAllClassesCheck;
    private Button searchBtn;
    private Button prevMatchBtn;
    private Button nextMatchBtn;
    private Label searchResultLabel;

    // Бизнес-логика
    private Decompiler decompiler;
    private JarPatcher patcher;
    private JavaCompiler javaCompiler;
    private SearchManager searchManager;

    // Данные
    private File currentJar;
    private String currentClassName;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Override
    public void start(Stage primaryStage) {
        setupExceptionHandling();
        this.primaryStage = primaryStage;

        try {
            initializeComponents();
            setupStage();
            createUI();
            primaryStage.show();
            updateStatus("Готов");
            updateCompilerStatus();
            System.out.println("✅ ByteCraft успешно запущен");
        } catch (Exception e) {
            handleStartupError(e);
        }
    }

    private void setupExceptionHandling() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("⚠️ НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ в потоке " + thread.getName() + ":");
            throwable.printStackTrace();
            Platform.runLater(() -> showExceptionDialog(throwable));
        });
    }

    private void initializeComponents() {
        System.out.println("🔄 Инициализация компонентов...");
        decompiler = new Decompiler();
        patcher = new JarPatcher();
        javaCompiler = new JavaCompiler();
        searchManager = new SearchManager(this, decompiler);
        System.out.println("✅ Компоненты инициализированы");
    }

    private void setupStage() {
        primaryStage.setTitle("ByteCraft - Декомпилятор и Репатчер");
        primaryStage.setWidth(1200);
        primaryStage.setHeight(800);
    }

    private void createUI() {
        root = UIStyles.createRootPane();

        root.setTop(createToolbar());
        root.setLeft(createSidebar());
        root.setCenter(createEditorArea());

        // Добавляем панель поиска над редактором
        BorderPane bottomContainer = new BorderPane();
        bottomContainer.setTop(createSearchPanel());
        bottomContainer.setBottom(createStatusBar());
        root.setBottom(bottomContainer);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        setupKeyBindings(scene);

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("👋 Закрытие приложения...");
            executor.shutdown();
        });
    }

    // ========== UI КОМПОНЕНТЫ ==========

    private HBox createToolbar() {
        HBox toolbar = UIStyles.createToolbar();

        Button openBtn = createToolbarButton("📁 Открыть плагин", this::openPlugin);
        Button decompileBtn = createToolbarButton("🔧 Декомпилировать", this::decompileSelected);
        Button patchBtn = createToolbarButton("⚡ Собрать патч", this::compileAndPack);
        Button advancedSearchBtn = createToolbarButton("🔍 Расширенный поиск", this::showAdvancedSearch);

        toolbar.getChildren().addAll(openBtn, decompileBtn, patchBtn, advancedSearchBtn);
        return toolbar;
    }

    private HBox createSearchPanel() {
        HBox searchPanel = UIStyles.createSearchPanel();

        // Поле поиска
        searchField = UIStyles.createSearchField();

        // Флажки
        caseSensitiveCheck = UIStyles.createCheckbox("Регистр");
        wholeWordCheck = UIStyles.createCheckbox("Слово целиком");
        regexCheck = UIStyles.createCheckbox("Регулярка");
        searchAllClassesCheck = UIStyles.createCheckbox("Во всех классах");

        // Кнопки поиска
        searchBtn = UIStyles.createButton("Найти");
        searchBtn.setOnAction(e -> performSearch());

        prevMatchBtn = UIStyles.createSmallButton("←");
        prevMatchBtn.setPrefWidth(30);
        prevMatchBtn.setOnAction(e -> searchManager.navigateToPrevMatch());
        prevMatchBtn.setDisable(true);

        nextMatchBtn = UIStyles.createSmallButton("→");
        nextMatchBtn.setPrefWidth(30);
        nextMatchBtn.setOnAction(e -> searchManager.navigateToNextMatch());
        nextMatchBtn.setDisable(true);

        // Информация о результатах
        searchResultLabel = UIStyles.createSearchResultLabel();

        // Обработка Enter в поле поиска
        searchField.setOnAction(e -> performSearch());

        searchPanel.getChildren().addAll(
                UIStyles.createLabel("Поиск:"), searchField,
                caseSensitiveCheck, wholeWordCheck, regexCheck, searchAllClassesCheck,
                searchBtn, prevMatchBtn, nextMatchBtn, searchResultLabel
        );

        HBox.setHgrow(searchResultLabel, Priority.ALWAYS);
        return searchPanel;
    }

    private Button createToolbarButton(String text, Runnable action) {
        Button btn = UIStyles.createToolbarButton(text);

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
        VBox sidebar = UIStyles.createSidebar();

        Label title = UIStyles.createTitleLabel("Классы плагина");

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
        return UIStyles.createEditorArea(codeArea);
    }

    private HBox createStatusBar() {
        HBox statusBar = UIStyles.createStatusBar();

        statusLabel = UIStyles.createLabel("Готов");
        progressBar = UIStyles.createProgressBar();

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label compilerLabel = UIStyles.createLabel("");

        statusBar.getChildren().addAll(statusLabel, spacer, progressBar, compilerLabel);
        return statusBar;
    }

    // ========== ФУНКЦИИ ПОИСКА ==========

    private void showAdvancedSearch() {
        Stage searchStage = new Stage();
        searchStage.initModality(Modality.WINDOW_MODAL);
        searchStage.initOwner(primaryStage);
        searchStage.setTitle("Расширенный поиск");
        searchStage.setWidth(600);
        searchStage.setHeight(400);

        VBox root = new VBox(10);
        root.setPadding(UIStyles.PADDING_LARGE);
        root.setStyle(UIStyles.getRootStyle());

        Label title = new Label("🔍 Расширенный поиск во всех классах");
        title.setStyle("-fx-text-fill: " + UIStyles.TEXT_WHITE + "; " +
                "-fx-font-size: 16px; -fx-font-weight: bold;");

        TextArea searchPatternArea = UIStyles.createTextArea();
        searchPatternArea.setPromptText("Введите текст для поиска или регулярное выражение...");
        searchPatternArea.setPrefHeight(100);

        HBox optionsBox = new HBox(15);
        optionsBox.setAlignment(Pos.CENTER_LEFT);

        CheckBox caseCheck = UIStyles.createCheckbox("Учитывать регистр");
        CheckBox regexCheckBox = UIStyles.createCheckbox("Регулярное выражение");
        CheckBox importCheck = UIStyles.createCheckbox("Искать импорты");

        optionsBox.getChildren().addAll(caseCheck, regexCheckBox, importCheck);

        Button searchAllBtn = UIStyles.createButton("🔍 Искать во всех классах");
        searchAllBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 20;");
        searchAllBtn.setOnAction(e -> {
            String pattern = searchPatternArea.getText().trim();
            if (pattern.isEmpty()) {
                showWarning("Пустой поиск", "Введите текст для поиска");
                return;
            }

            boolean caseSensitive = caseCheck.isSelected();
            boolean useRegex = regexCheckBox.isSelected();
            boolean searchImports = importCheck.isSelected();

            if (searchImports) {
                pattern = "import.*" + Pattern.quote(pattern) + ".*;";
                useRegex = true;
            }

            searchManager.performSearch(pattern, true, caseSensitive, false, useRegex);
            searchStage.close();
        });

        Button cancelBtn = UIStyles.createSmallButton("Отмена");
        cancelBtn.setOnAction(e -> searchStage.close());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(cancelBtn, searchAllBtn);

        root.getChildren().addAll(title, searchPatternArea, optionsBox, buttonBox);

        Scene scene = new Scene(root);
        searchStage.setScene(scene);
        searchStage.show();
    }

    private void performSearch() {
        searchManager.performSearch(
                searchField.getText().trim(),
                searchAllClassesCheck.isSelected(),
                caseSensitiveCheck.isSelected(),
                wholeWordCheck.isSelected(),
                regexCheck.isSelected()
        );
    }

    private void setupKeyBindings(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.F) {
                if (searchField != null) {
                    searchField.requestFocus();
                    searchField.selectAll();
                    event.consume();
                }
            } else if (event.getCode() == javafx.scene.input.KeyCode.F3) {
                if (event.isShiftDown()) {
                    searchManager.navigateToPrevMatch();
                } else {
                    searchManager.navigateToNextMatch();
                }
                event.consume();
            } else if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.G) {
                showAdvancedSearch();
                event.consume();
            }
        });
    }

    // ========== ОСНОВНЫЕ ФУНКЦИИ ==========

    private void updateCompilerStatus() {
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

            Platform.runLater(() -> {
                Label compilerLabel = (Label) ((HBox) ((BorderPane) this.root.getBottom()).getBottom()).getChildren().get(3);
                compilerLabel.setText(javaCompiler.getCompilerInfo());
            });

        } catch (Exception e) {
            System.err.println("❌ Ошибка при проверке компилятора:");
            e.printStackTrace();
        }
    }

    public void openPlugin() {
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

                // Сбрасываем поиск
                searchManager.resetSearch();
                searchResultLabel.setText("");
                searchField.setText("");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка при открытии плагина:");
            e.printStackTrace();
            showError("Ошибка открытия", e.getMessage());
        }
    }

    public void loadClassTree() {
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

    public void decompileSelected() {
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

    public void decompileClass(String className) {
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

                    // Сбрасываем поиск при смене класса
                    searchManager.resetSearch();
                    searchResultLabel.setText("");
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

    public void compileAndPack() {
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
        Alert confirm = UIStyles.createConfirmAlert("Подтверждение", "Собрать патч?",
                "Класс: " + currentClassName + "\nБудет создан новый JAR файл рядом с оригиналом.");

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

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private void showExceptionDialog(Throwable throwable) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Необработанная ошибка");
        alert.setHeaderText("Произошла ошибка в потоке: " + Thread.currentThread().getName());

        TextArea textArea = new TextArea(getStackTrace(throwable));
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        alert.getDialogPane().setExpandableContent(new ScrollPane(textArea));
        alert.getDialogPane().setExpanded(true);
        alert.setContentText(throwable.getMessage());
        alert.showAndWait();
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private void handleStartupError(Exception e) {
        System.err.println("❌ Ошибка при запуске IDE:");
        e.printStackTrace();
        showCriticalError("Ошибка запуска", e);
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

    // ИЗМЕНЕНО: убрали модификатор private
    void updateStatus(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            System.out.println("[STATUS] " + message);
        });
    }

    // ИЗМЕНЕНО: убрали модификатор private
    void showProgress(boolean show) {
        Platform.runLater(() -> {
            progressBar.setVisible(show);
            progressBar.setProgress(show ? ProgressBar.INDETERMINATE_PROGRESS : 0);
        });
    }

    // ИЗМЕНЕНО: убрали модификатор private
    void showError(String title, String message) {
        Platform.runLater(() -> {
            System.err.println("[ERROR] " + title + ": " + message);
            Alert alert = UIStyles.createErrorAlert(title, message);
            alert.showAndWait();
        });
    }

    // ИЗМЕНЕНО: убрали модификатор private
    void showWarning(String title, String message) {
        Platform.runLater(() -> {
            System.out.println("[WARN] " + title + ": " + message);
            Alert alert = UIStyles.createWarningAlert(title, message);
            alert.showAndWait();
        });
    }

    // ИЗМЕНЕНО: убрали модификатор private
    void showInfo(String title, String message) {
        Platform.runLater(() -> {
            System.out.println("[INFO] " + title + ": " + message);
            Alert alert = UIStyles.createInfoAlert(title, message);
            alert.showAndWait();
        });
    }

    // ========== ГЕТТЕРЫ ДЛЯ SearchManager ==========

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public File getCurrentJar() {
        return currentJar;
    }

    public TextArea getCodeArea() {
        return codeArea;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void setSearchResultLabel(String text) {
        Platform.runLater(() -> searchResultLabel.setText(text));
    }

    public void setSearchNavigationDisabled(boolean disabled) {
        Platform.runLater(() -> {
            prevMatchBtn.setDisable(disabled);
            nextMatchBtn.setDisable(disabled);
        });
    }

    public void setSearchField(String text) {
        Platform.runLater(() -> searchField.setText(text));
    }

    public void setSearchOptions(boolean caseSensitive, boolean wholeWord, boolean regex) {
        Platform.runLater(() -> {
            caseSensitiveCheck.setSelected(caseSensitive);
            wholeWordCheck.setSelected(wholeWord);
            regexCheck.setSelected(regex);
        });
    }

    @Override
    public void stop() {
        System.out.println("🛑 Остановка приложения...");
        executor.shutdown();
        System.out.println("👋 ByteCraft завершил работу");
    }
}