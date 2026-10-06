package io.github.bargainbinbastard.altus.history;

/** Prints a full history report for a seed. Usage: {@code HistoryCli <seed>}. */
public final class HistoryCli {
    public static void main(String[] args) {
        String seed = args.length > 0 ? args[0] : "altus-1";
        System.out.print(HistoryReport.render(HistorySimulator.simulate(seed)));
    }
}
