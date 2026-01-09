package su.bytecraft.ide;

import java.util.List;

public class SearchResult {
    private final String displayName;
    private final String originalClassName;
    private final List<TextPosition> matches;

    public SearchResult(String displayName, String originalClassName, List<TextPosition> matches) {
        this.displayName = displayName;
        this.originalClassName = originalClassName;
        this.matches = matches;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOriginalClassName() {
        return originalClassName;
    }

    public List<TextPosition> getMatches() {
        return matches;
    }

    public int getMatchCount() {
        return matches.size();
    }

    @Override
    public String toString() {
        return displayName + " (" + matches.size() + " совпадений)";
    }

    public static class TextPosition {
        public final int start;
        public final int end;
        public final int line;
        public final int column;

        public TextPosition(int start, int end, int line, int column) {
            this.start = start;
            this.end = end;
            this.line = line;
            this.column = column;
        }
    }
}