package life.hebo;

public class ClientConfig {

    private ClientConfig() {}

    // ── Server ───────────────────────────────────────────────
    public static final String SERVER_URI = "ws://localhost:8080/chat/";

    // Messages
    public static final int MAX_USER_ID = 100000;
    public static final int TOTAL_MESSAGES = 500000;
    public static final int NUM_ROOMS = 20;
    public static final double TEXT_RATIO = 90;
    public static final double JOIN_RATIO = 5;

    // Warmup Phase
    public static final int WARMUP_THREADS = 32;
    public static final int WARMUP_MESSAGES_PER_THREAD = 1000;
    public static final int WARMUP_TOTAL_MESSAGES = WARMUP_THREADS * WARMUP_MESSAGES_PER_THREAD;

    // Main Phase
    public static final int MAIN_PHASE_THREADS  = 64;
    public static final int MAIN_PHASE_MESSAGES = TOTAL_MESSAGES - WARMUP_TOTAL_MESSAGES;

    // Queue
    public static final int QUEUE_CAPACITY = 50000;

    // Retry
    public static final int  MAX_RETRIES = 5;
    public static final long INITIAL_BACKOFF_MS = 50;

    // Metrics
    public static final String CSV_FILE_PATH = "results/metrics.csv";
    public static final String CHART_FILE_PATH = "results/throughput_chart.png";
    public static final int BUCKET_SECONDS = 1;

    // Predefined message pool
    public static final String[] MESSAGES_POOL = {
            "01. This is message #01 from the message pool.",
            "02. This is message #02 from the message pool.",
            "03. This is message #03 from the message pool.",
            "04. This is message #04 from the message pool.",
            "05. This is message #05 from the message pool.",
            "06. This is message #06 from the message pool.",
            "07. This is message #07 from the message pool.",
            "08. This is message #08 from the message pool.",
            "09. This is message #09 from the message pool.",
            "10. This is message #10 from the message pool.",
            "11. This is message #11 from the message pool.",
            "12. This is message #12 from the message pool.",
            "13. This is message #13 from the message pool.",
            "14. This is message #14 from the message pool.",
            "15. This is message #15 from the message pool.",
            "16. This is message #16 from the message pool.",
            "17. This is message #17 from the message pool.",
            "18. This is message #18 from the message pool.",
            "19. This is message #19 from the message pool.",
            "20. This is message #20 from the message pool.",
            "21. This is message #21 from the message pool.",
            "22. This is message #22 from the message pool.",
            "23. This is message #23 from the message pool.",
            "24. This is message #24 from the message pool.",
            "25. This is message #25 from the message pool.",
            "26. This is message #26 from the message pool.",
            "27. This is message #27 from the message pool.",
            "28. This is message #28 from the message pool.",
            "29. This is message #29 from the message pool.",
            "30. This is message #30 from the message pool.",
            "31. This is message #31 from the message pool.",
            "32. This is message #32 from the message pool.",
            "33. This is message #33 from the message pool.",
            "34. This is message #34 from the message pool.",
            "35. This is message #35 from the message pool.",
            "36. This is message #36 from the message pool.",
            "37. This is message #37 from the message pool.",
            "38. This is message #38 from the message pool.",
            "39. This is message #39 from the message pool.",
            "40. This is message #40 from the message pool.",
            "41. This is message #41 from the message pool.",
            "42. This is message #42 from the message pool.",
            "43. This is message #43 from the message pool.",
            "44. This is message #44 from the message pool.",
            "45. This is message #45 from the message pool.",
            "46. This is message #46 from the message pool.",
            "47. This is message #47 from the message pool.",
            "48. This is message #48 from the message pool.",
            "49. This is message #49 from the message pool.",
            "50. This is message #50 from the message pool."
    };
}
