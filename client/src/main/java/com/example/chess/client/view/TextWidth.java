package com.example.chess.client.view;

final class TextWidth {

    private TextWidth() {}

    static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);

            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) continue;

            w += isWide(cp) ? 2 : 1;
        }
        return w;
    }

    static boolean isWide(int cp) {
        if (cp == 0x2B1B || cp == 0x2B1C) return true;
        return cp >= 0x1F300 && cp <= 0x1FAFF;
    }
}
