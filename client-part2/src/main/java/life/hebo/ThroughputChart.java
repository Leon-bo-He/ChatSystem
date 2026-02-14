package life.hebo;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ThroughputChart {

    public static void generate(Map<Long, Integer> buckets, int bucketSizeSec, String outputPath) {
        if (buckets == null || buckets.isEmpty()) {
            return;
        }

        long firstBucket = buckets.keySet().stream().mapToLong(Long::longValue).min().orElse(0);
        XYSeries series = new XYSeries("Throughput");

        for (Map.Entry<Long, Integer> entry : buckets.entrySet()) {
            long bucketKey = entry.getKey();
            double elapsedSec = (double) (bucketKey - firstBucket);
            double msgPerSec = (double) entry.getValue() / bucketSizeSec;
            series.add(elapsedSec, msgPerSec);
        }

        if (series.getItemCount() == 0) {
            return;
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Throughput Over Time",
                "Elapsed Time (s)",
                "Messages / Second",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setDefaultShapesVisible(true);
        renderer.setDefaultShapesFilled(true);
        plot.setRenderer(renderer);

        try {
            Path parent = Path.of(outputPath).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ChartUtils.saveChartAsPNG(new File(outputPath), chart, 1024, 500);
        } catch (IOException e) {
            System.err.println("Failed to save chart: " + e.getMessage());
        }
    }
}
