package life.hebo;

public class LittlesLawAnalysis {

    public static void printAnalysis(int threads, double estimatedRttMs, double actualThroughput) {
        double W = estimatedRttMs / 1000.0;            // convert to seconds
        double predictedThroughput = threads / W;      // λ = L / W

        System.out.println("\n── Little's Law Analysis ────────────────────────────");
        System.out.printf("  Concurrent threads (L) : %d%n", threads);
        System.out.printf("  Estimated RTT (W)      : %.2f ms%n", estimatedRttMs);
        System.out.printf("  Predicted throughput   : %,.0f msg/s%n", predictedThroughput);

        if (actualThroughput > 0) {
            double efficiency = (actualThroughput / predictedThroughput) * 100.0;
            System.out.printf("  Actual throughput      : %,.0f msg/s%n", actualThroughput);
            System.out.printf("  Efficiency             : %.1f%%%n", efficiency);
        }

        System.out.println("─────────────────────────────────────────────────────");
    }
}
