package com.example.chess.client.view;

final class ChessBoardRenderer implements BoardRenderer {

    private static final String DARK  = "##";
    private static final String LIGHT = "...";

    @Override
    public String render(String boardText, boolean isWhitePerspective) {
        char[][] grid = BoardTextParser.tryParse(boardText);
        if (grid == null) return boardText;

        int[] files = isWhitePerspective
                ? new int[]{0,1,2,3,4,5,6,7}
                : new int[]{7,6,5,4,3,2,1,0};

        int startRank = isWhitePerspective ? 8 : 1;
        int endRank   = isWhitePerspective ? 1 : 8;
        int step      = isWhitePerspective ? -1 : 1;

        StringBuilder sb = new StringBuilder();

        for (int rank = startRank; ; rank += step) {
            int row = 8 - rank;

            sb.append(rank).append("  "); // left rank only

            for (int i = 0; i < 8; i++) {
                int file = files[i];
                char pc = grid[row][file];

                if (pc == '.') {
                    boolean dark = ((rank + file) % 2 == 1);
                    sb.append(dark ? DARK : LIGHT);
                } else {
                    sb.append(ChessGlyphs.pieceCell(pc));
                }
            }

            sb.append('\n');
            if (rank == endRank) break;
        }

        appendFilesHeaderBottom(sb, files);
        return sb.toString();
    }

    private static void appendFilesHeaderBottom(StringBuilder sb, int[] files) {
        sb.append("   ");
        for (int i = 0; i < 8; i++) {
            char fileChar = (char)('a' + files[i]);
            sb.append(fileChar);

            if (i <= 2) sb.append("  ");
            else if (i == 3 || i == 4) sb.append(" ");
            else sb.append("  ");
        }
        sb.append('\n');
    }
}