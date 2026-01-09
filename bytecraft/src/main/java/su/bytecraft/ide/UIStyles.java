package su.bytecraft.ide;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class UIStyles {

    // Цветовая палитра
    public static final String BACKGROUND_DARK = "#1e1e1e";
    public static final String BACKGROUND_DARKER = "#252526";
    public static final String BACKGROUND_DARKEST = "#2d2d30";
    public static final String BACKGROUND_PANEL = "#3e3e42";
    public static final String PRIMARY_BLUE = "#007acc";
    public static final String PRIMARY_BLUE_HOVER = "#0088dd";
    public static final String TEXT_WHITE = "white";
    public static final String TEXT_GRAY = "#cccccc";
    public static final String TEXT_LIGHT_BLUE = "#4ec9b0";

    // Размеры
    public static final Insets PADDING_SMALL = new Insets(5);
    public static final Insets PADDING_MEDIUM = new Insets(10);
    public static final Insets PADDING_LARGE = new Insets(15);
    public static final Insets PADDING_TOOLBAR = new Insets(10);
    public static final Insets PADDING_STATUS_BAR = new Insets(5, 10, 5, 10);

    // Стили компонентов
    public static String getRootStyle() {
        return "-fx-background-color: " + BACKGROUND_DARK + ";";
    }

    public static String getToolbarStyle() {
        return "-fx-background-color: " + BACKGROUND_DARKER + ";";
    }

    public static String getSidebarStyle() {
        return "-fx-background-color: " + BACKGROUND_DARKER + ";";
    }

    public static String getSearchPanelStyle() {
        return "-fx-background-color: " + BACKGROUND_DARKEST + ";";
    }

    public static String getStatusBarStyle() {
        return "-fx-background-color: " + PRIMARY_BLUE + ";";
    }

    public static String getEditorStyle() {
        return "-fx-font-family: 'Consolas', monospace; " +
                "-fx-font-size: 13px; " +
                "-fx-control-inner-background: " + BACKGROUND_DARK + "; " +
                "-fx-text-fill: " + TEXT_GRAY + ";";
    }

    public static String getTextFieldStyle() {
        return "-fx-background-color: " + BACKGROUND_PANEL + "; " +
                "-fx-text-fill: " + TEXT_WHITE + ";";
    }

    public static String getCheckboxStyle() {
        return "-fx-text-fill: " + TEXT_GRAY + ";";
    }

    public static String getButtonStyle() {
        return "-fx-background-color: " + PRIMARY_BLUE + "; " +
                "-fx-text-fill: " + TEXT_WHITE + "; " +
                "-fx-font-size: 13px; " +
                "-fx-padding: 8 15; " +
                "-fx-cursor: hand;";
    }

    public static String getButtonHoverStyle() {
        return "-fx-background-color: " + PRIMARY_BLUE_HOVER + "; " +
                "-fx-text-fill: " + TEXT_WHITE + "; " +
                "-fx-font-size: 13px; " +
                "-fx-padding: 8 15; " +
                "-fx-cursor: hand;";
    }

    public static String getSmallButtonStyle() {
        return "-fx-background-color: " + BACKGROUND_PANEL + "; " +
                "-fx-text-fill: " + TEXT_WHITE + ";";
    }

    public static String getLabelStyle() {
        return "-fx-text-fill: " + TEXT_WHITE + ";";
    }

    public static String getLabelGrayStyle() {
        return "-fx-text-fill: " + TEXT_GRAY + ";";
    }

    public static String getLabelBoldStyle() {
        return "-fx-text-fill: " + TEXT_GRAY + "; " +
                "-fx-font-weight: bold;";
    }

    public static String getSearchResultStyle() {
        return "-fx-text-fill: " + TEXT_LIGHT_BLUE + "; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold;";
    }

    // Фабричные методы для создания компонентов
    public static BorderPane createRootPane() {
        BorderPane root = new BorderPane();
        root.setStyle(getRootStyle());
        return root;
    }

    public static HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(PADDING_TOOLBAR);
        toolbar.setStyle(getToolbarStyle());
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    public static VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(250);
        sidebar.setStyle(getSidebarStyle());
        sidebar.setPadding(PADDING_MEDIUM);
        return sidebar;
    }

    public static ScrollPane createEditorArea(TextArea codeArea) {
        codeArea.setStyle(getEditorStyle());
        codeArea.setEditable(true);

        ScrollPane scrollPane = new ScrollPane(codeArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        return scrollPane;
    }

    public static TextArea createTextArea() {
        TextArea textArea = new TextArea();
        textArea.setStyle(getEditorStyle());
        return textArea;
    }

    public static HBox createSearchPanel() {
        HBox searchPanel = new HBox(5);
        searchPanel.setPadding(PADDING_SMALL);
        searchPanel.setStyle(getSearchPanelStyle());
        searchPanel.setAlignment(Pos.CENTER_LEFT);
        return searchPanel;
    }

    public static TextField createSearchField() {
        TextField searchField = new TextField();
        searchField.setPromptText("Поиск...");
        searchField.setPrefWidth(200);
        searchField.setStyle(getTextFieldStyle());
        return searchField;
    }

    public static CheckBox createCheckbox(String text) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setStyle(getCheckboxStyle());
        return checkBox;
    }

    public static Button createButton(String text) {
        Button button = new Button(text);
        button.setStyle(getButtonStyle());
        return button;
    }

    public static Button createSmallButton(String text) {
        Button button = new Button(text);
        button.setStyle(getSmallButtonStyle());
        return button;
    }

    public static Button createToolbarButton(String text) {
        Button button = createButton(text);

        button.setOnMouseEntered(e -> button.setStyle(getButtonHoverStyle()));
        button.setOnMouseExited(e -> button.setStyle(getButtonStyle()));

        return button;
    }

    public static Label createLabel(String text) {
        Label label = new Label(text);
        label.setStyle(getLabelStyle());
        return label;
    }

    public static Label createLabelGray(String text) {
        Label label = new Label(text);
        label.setStyle(getLabelGrayStyle());
        return label;
    }

    public static Label createLabelBold(String text) {
        Label label = new Label(text);
        label.setStyle(getLabelBoldStyle());
        return label;
    }

    public static Label createSearchResultLabel() {
        Label label = new Label("");
        label.setStyle("-fx-text-fill: " + TEXT_GRAY + "; -fx-font-size: 12px;");
        label.setPadding(new Insets(0, 0, 0, 10));
        return label;
    }

    public static HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(PADDING_STATUS_BAR);
        statusBar.setStyle(getStatusBarStyle());
        return statusBar;
    }

    public static ProgressBar createProgressBar() {
        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setPrefWidth(150);
        return progressBar;
    }

    public static Label createTitleLabel(String text) {
        Label title = createLabelBold(text);
        title.setPadding(new Insets(0, 0, 10, 0));
        return title;
    }

    // Диалоги
    public static Alert createErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert;
    }

    public static Alert createWarningAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert;
    }

    public static Alert createInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert;
    }

    public static Alert createConfirmAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        return alert;
    }

    // Стили для предпросмотра кода
    public static String getPreviewAreaStyle() {
        return "-fx-font-family: 'Consolas', monospace; " +
                "-fx-control-inner-background: " + BACKGROUND_DARKER + "; " +
                "-fx-text-fill: " + TEXT_WHITE + ";";
    }

    // Стиль для ListView результатов поиска
    public static String getResultsListStyle() {
        return "-fx-background-color: " + BACKGROUND_DARK + "; " +
                "-fx-text-fill: " + TEXT_WHITE + "; " +
                "-fx-font-family: 'Consolas', monospace;";
    }
}