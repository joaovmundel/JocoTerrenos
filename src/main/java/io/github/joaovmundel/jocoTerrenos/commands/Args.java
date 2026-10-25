package io.github.joaovmundel.jocoTerrenos.commands;

/**
 * Helpers for command arguments. Land names may have spaces, so a name is several arguments joined back.
 */
final class Args {

    private Args() {
    }

    /** @return the arguments from {@code start} (inclusive) to {@code end} (exclusive) joined by single spaces */
    static String join(String[] args, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
