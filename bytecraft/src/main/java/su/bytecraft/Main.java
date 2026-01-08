package su.bytecraft;

import javafx.application.Application;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("Запуск ByteCraft...");
            System.out.println("Java: " + System.getProperty("java.version"));
            System.out.println("Java Home: " + System.getProperty("java.home"));

            // Включаем подробное логирование
            System.setProperty("javafx.verbose", "true");
            System.setProperty("prism.verbose", "true");

            Application.launch(IDE.class, args);

        } catch (Throwable t) {
            System.err.println("КРИТИЧЕСКАЯ ОШИБКА:");
            t.printStackTrace();

            // Показываем диалог для GUI ошибок
            javax.swing.JOptionPane.showMessageDialog(null,
                    "Ошибка запуска: " + t.getMessage() +
                            "\n\nПроверьте консоль для деталей.",
                    "ByteCraft - Ошибка",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
}