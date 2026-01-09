package su.bytecraft.ide;

import javafx.geometry.Pos;
import su.bytecraft.Decompiler;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchManager {

    private final IDE ide;
    private final Decompiler decompiler;

    // Для поиска
    private List<SearchResult.TextPosition> searchResults = new ArrayList<>();
    private int currentSearchIndex = -1;
    private String lastSearchText = "";

    public SearchManager(IDE ide, Decompiler decompiler) {
        this.ide = ide;
        this.decompiler = decompiler;
    }

    public void performSearch(String searchText, boolean searchAllClasses,
                              boolean caseSensitive, boolean wholeWord, boolean useRegex) {
        if (searchText.isEmpty()) {
            // Используем метод IDE напрямую
            ide.showWarning("Пустой поиск", "Введите текст для поиска");
            return;
        }

        lastSearchText = searchText;

        if (searchAllClasses) {
            // Поиск во всех классах
            performGlobalSearch(searchText, caseSensitive, useRegex);
        } else {
            // Поиск в текущем классе
            searchInCurrentClass(searchText, caseSensitive, wholeWord, useRegex);
        }
    }

    private void searchInCurrentClass(String searchText, boolean caseSensitive,
                                      boolean wholeWord, boolean useRegex) {
        String text = ide.getCodeArea().getText();
        if (text.isEmpty()) {
            // Используем метод IDE напрямую
            ide.showWarning("Нет кода", "Сначала декомпилируйте класс");
            return;
        }

        searchResults.clear();
        currentSearchIndex = -1;

        try {
            Pattern pattern = buildPattern(searchText, caseSensitive, wholeWord, useRegex);

            // Ищем все совпадения
            java.util.regex.Matcher matcher = pattern.matcher(text);
            final List<SearchResult.TextPosition> foundResults = new ArrayList<>();
            int count = 0;

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                // Вычисляем строку и колонку
                String before = text.substring(0, start);
                int line = before.split("\n", -1).length;
                int column = start - before.lastIndexOf('\n');

                foundResults.add(new SearchResult.TextPosition(start, end, line, column));
                count++;
            }

            // Обновляем результаты поиска
            searchResults = foundResults;
            final int finalCount = count;

            if (count == 0) {
                Platform.runLater(() -> {
                    ide.setSearchResultLabel("Совпадений не найдено");
                    ide.setSearchNavigationDisabled(true);
                });
                // Используем метод IDE напрямую
                ide.showInfo("Поиск", "Совпадений не найдено");
            } else {
                Platform.runLater(() -> {
                    ide.setSearchResultLabel("Найдено: " + finalCount + " совпадений");
                    ide.setSearchNavigationDisabled(false);

                    // Переходим к первому совпадению
                    if (!searchResults.isEmpty()) {
                        currentSearchIndex = 0;
                        highlightCurrentMatch();
                    }
                });

                // Используем метод IDE напрямую
                ide.showInfo("Поиск", "Найдено " + finalCount + " совпадений");
            }

        } catch (PatternSyntaxException e) {
            // Используем метод IDE напрямую
            ide.showError("Ошибка регулярного выражения",
                    "Некорректное регулярное выражение: " + e.getMessage());
        }
    }

    private Pattern buildPattern(String searchText, boolean caseSensitive,
                                 boolean wholeWord, boolean useRegex) {
        if (useRegex) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(searchText, flags);
        } else {
            String regex = Pattern.quote(searchText);
            if (wholeWord) {
                regex = "\\b" + regex + "\\b";
            }
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(regex, flags);
        }
    }

    private void performGlobalSearch(String searchText, boolean caseSensitive, boolean useRegex) {
        File currentJar = ide.getCurrentJar();
        if (currentJar == null) {
            // Используем метод IDE напрямую
            ide.showWarning("Нет плагина", "Сначала откройте плагин");
            return;
        }

        ide.updateStatus("Поиск во всех классах...");
        ide.showProgress(true);

        ide.getExecutor().submit(() -> {
            try {
                System.out.println("🔍 Глобальный поиск: '" + searchText + "'");
                System.out.println("   Регистр: " + caseSensitive + ", Регулярка: " + useRegex);

                // Получаем список всех классов
                List<String> allClasses = decompiler.getClassesFromJar(currentJar);
                System.out.println("   Всего классов для поиска: " + allClasses.size());

                List<SearchResult> results = new ArrayList<>();
                int totalMatches = 0;

                // Ограничиваем количество проверяемых классов для скорости
                int maxClassesToCheck = 100;
                int checkedCount = 0;

                for (String className : allClasses) {
                    if (checkedCount >= maxClassesToCheck) {
                        System.out.println("   ⚠️  Достигнут лимит в " + maxClassesToCheck + " классов");
                        break;
                    }

                    try {
                        // Декомпилируем каждый класс
                        String code = decompiler.decompileClassFromJar(currentJar, className);
                        checkedCount++;

                        // Ищем совпадения
                        Pattern pattern = buildPattern(searchText, caseSensitive, false, useRegex);
                        java.util.regex.Matcher matcher = pattern.matcher(code);
                        List<SearchResult.TextPosition> matches = new ArrayList<>();

                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();

                            String before = code.substring(0, start);
                            int line = before.split("\n", -1).length;
                            int column = start - before.lastIndexOf('\n');

                            matches.add(new SearchResult.TextPosition(start, end, line, column));
                        }

                        if (!matches.isEmpty()) {
                            String displayName = className.replace(".class", "").replace("/", ".");
                            results.add(new SearchResult(displayName, className, matches));
                            totalMatches += matches.size();

                            System.out.println("   ✅ " + displayName + " - найдено: " + matches.size());
                        }

                    } catch (Exception e) {
                        System.err.println("   ⚠️  Ошибка при декомпиляции " + className + ": " + e.getMessage());
                    }
                }

                final List<SearchResult> finalResults = new ArrayList<>(results);
                final int finalTotalMatches = totalMatches;
                final int finalCheckedCount = checkedCount;

                Platform.runLater(() -> {
                    ide.showProgress(false);

                    if (finalResults.isEmpty()) {
                        ide.setSearchResultLabel("Совпадений не найдено");
                        // Используем метод IDE напрямую
                        ide.showInfo("Глобальный поиск",
                                "Совпадений не найдено в " + finalCheckedCount + " классах");
                    } else {
                        ide.setSearchResultLabel("Найдено в " + finalResults.size() +
                                " классах: " + finalTotalMatches + " совпадений");
                        showSearchResultsDialog(finalResults, searchText,
                                finalTotalMatches, finalCheckedCount);
                    }

                    ide.updateStatus("Готов");
                });

            } catch (Exception e) {
                System.err.println("❌ Ошибка при глобальном поиске:");
                e.printStackTrace();
                Platform.runLater(() -> {
                    ide.showProgress(false);
                    // Используем метод IDE напрямую
                    ide.showError("Ошибка поиска", e.getMessage());
                    ide.updateStatus("Ошибка поиска");
                });
            }
        });
    }

    public void navigateToPrevMatch() {
        if (searchResults.isEmpty()) return;

        currentSearchIndex--;
        if (currentSearchIndex < 0) {
            currentSearchIndex = searchResults.size() - 1;
        }

        highlightCurrentMatch();
    }

    public void navigateToNextMatch() {
        if (searchResults.isEmpty()) return;

        currentSearchIndex++;
        if (currentSearchIndex >= searchResults.size()) {
            currentSearchIndex = 0;
        }

        highlightCurrentMatch();
    }

    private void highlightCurrentMatch() {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchResults.size()) return;

        SearchResult.TextPosition pos = searchResults.get(currentSearchIndex);

        // Выделяем текст
        ide.getCodeArea().selectRange(pos.start, pos.end);
        ide.getCodeArea().requestFocus();

        // Обновляем информацию
        ide.setSearchResultLabel("Совпадение " + (currentSearchIndex + 1) + " из " +
                searchResults.size() + " (строка " + pos.line + ")");
    }

    private void showSearchResultsDialog(List<SearchResult> results, String searchText,
                                         int totalMatches, int checkedClasses) {
        Stage resultsStage = new Stage();
        resultsStage.initModality(Modality.WINDOW_MODAL);
        resultsStage.initOwner(ide.getPrimaryStage());
        resultsStage.setTitle("Результаты поиска: '" + searchText + "'");
        resultsStage.setWidth(800);
        resultsStage.setHeight(600);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle(UIStyles.getRootStyle());

        Label summaryLabel = new Label("🔍 Найдено " + totalMatches + " совпадений в " +
                results.size() + " классах (проверено " + checkedClasses + ")");
        summaryLabel.setStyle(UIStyles.getSearchResultStyle());

        ListView<SearchResult> resultsList = new ListView<>();
        resultsList.setItems(FXCollections.observableArrayList(results));
        resultsList.setCellFactory(lv -> new ListCell<SearchResult>() {
            @Override
            protected void updateItem(SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.getDisplayName() + " (" + item.getMatchCount() + " совпадений)");
                    setStyle(UIStyles.getResultsListStyle());
                }
            }
        });

        // Предпросмотр кода
        TextArea previewArea = UIStyles.createTextArea();
        previewArea.setEditable(false);
        previewArea.setStyle(UIStyles.getPreviewAreaStyle());
        previewArea.setPrefHeight(200);

        resultsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                try {
                    // Декомпилируем выбранный класс для предпросмотра
                    String code = decompiler.decompileClassFromJar(ide.getCurrentJar(),
                            newVal.getOriginalClassName());

                    // Подсвечиваем найденные места
                    StringBuilder highlighted = new StringBuilder();
                    String[] lines = code.split("\n");

                    // Находим строки с совпадениями
                    Set<Integer> highlightLines = new HashSet<>();
                    for (SearchResult.TextPosition pos : newVal.getMatches()) {
                        highlightLines.add(pos.line);
                    }

                    for (int i = 0; i < lines.length; i++) {
                        if (highlightLines.contains(i + 1)) {
                            highlighted.append(">>> ");
                        }
                        highlighted.append(lines[i]).append("\n");
                    }

                    previewArea.setText(highlighted.toString());

                    // Прокручиваем к первому совпадению
                    if (!newVal.getMatches().isEmpty()) {
                        int firstLine = newVal.getMatches().get(0).line;
                        previewArea.setScrollTop(firstLine * 16); // Примерная высота строки
                    }
                } catch (Exception e) {
                    previewArea.setText("Ошибка загрузки класса: " + e.getMessage());
                }
            }
        });

        // Кнопки
        Button openClassBtn = UIStyles.createButton("📖 Открыть класс");
        openClassBtn.setOnAction(e -> {
            SearchResult selected = resultsList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                resultsStage.close();
                ide.decompileClass(selected.getOriginalClassName());

                // После декомпиляции ищем те же совпадения в этом классе
                Platform.runLater(() -> {
                    ide.setSearchField(searchText);
                    ide.setSearchOptions(!searchText.equals(searchText.toLowerCase()),
                            false, false);

                    // Запускаем поиск в этом классе
                    searchInCurrentClass(searchText,
                            !searchText.equals(searchText.toLowerCase()),
                            false, false);
                });
            }
        });

        Button closeBtn = UIStyles.createSmallButton("Закрыть");
        closeBtn.setOnAction(e -> resultsStage.close());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(closeBtn, openClassBtn);

        VBox previewBox = new VBox(5);
        previewBox.getChildren().addAll(UIStyles.createLabel("Предпросмотр:"), previewArea);

        root.getChildren().addAll(summaryLabel, resultsList, previewBox, buttonBox);
        VBox.setVgrow(resultsList, Priority.ALWAYS);

        Scene scene = new Scene(root);
        resultsStage.setScene(scene);
        resultsStage.show();
    }

    public void resetSearch() {
        searchResults.clear();
        currentSearchIndex = -1;
    }

    // Геттеры
    public String getLastSearchText() {
        return lastSearchText;
    }

    public boolean hasSearchResults() {
        return !searchResults.isEmpty();
    }

    public int getCurrentSearchIndex() {
        return currentSearchIndex;
    }

    public int getSearchResultsCount() {
        return searchResults.size();
    }
}