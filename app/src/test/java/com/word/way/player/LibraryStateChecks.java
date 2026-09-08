package com.word.way.player;

public final class LibraryStateChecks {
    private static void expect(LibraryState expected, boolean loading, boolean failed, int total, int visible) {
        if (LibraryState.resolve(loading, failed, total, visible) != expected) throw new AssertionError("Wrong library state");
    }
    public static void runAll() {
        for (int total = 0; total <= 100; total++) {
            for (int visible = 0; visible <= total; visible++) {
                expect(LibraryState.LOADING, true, false, total, visible);
                expect(LibraryState.LOADING, true, true, total, visible);
                expect(LibraryState.ERROR, false, true, total, visible);
                expect(total == 0 ? LibraryState.EMPTY : visible == 0 ? LibraryState.NO_MATCHES : LibraryState.CONTENT,
                        false, false, total, visible);
            }
        }
        for (int[] counts : new int[][]{{-1, 0}, {0, -1}, {1, 2}}) {
            try { LibraryState.resolve(false, false, counts[0], counts[1]); throw new AssertionError("Invalid counts accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    public static void main(String[] args) {
        runAll();
        System.out.println("PASS: all library state/count combinations through 100 files, including invalid counts");
    }
}
