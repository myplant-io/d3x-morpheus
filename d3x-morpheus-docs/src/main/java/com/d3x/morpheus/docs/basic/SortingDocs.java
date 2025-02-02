/*
 * Copyright (C) 2014-2018 D3X Systems - All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.d3x.morpheus.docs.basic;

import java.awt.*;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.frame.DataFrameRow;
import com.d3x.morpheus.range.Range;
import com.d3x.morpheus.util.Collect;
import com.d3x.morpheus.util.PerfStat;
import com.d3x.morpheus.util.text.parser.Parser;
import com.d3x.morpheus.viz.chart.Chart;

public class SortingDocs {

    /**
     * Returns the ATP match results for the year specified
     * @param year      the year for ATP results
     * @return          the ATP match results
     */
    static DataFrame<Integer,String> loadTennisMatchData(int year) {
        var dateFormat = DateTimeFormatter.ofPattern("dd/MM/yy");
        return DataFrame.read().csv(options -> {
            options.setHeader(true);
            options.setResource("http://www.zavtech.com/data/tennis/atp/atp-" + year + ".csv");
            options.setExcludeColumns("ATP");
            options.setParser("Date", Parser.ofLocalDate(dateFormat));
        });
    }


    @Test()
    public void sortRowAxis() {
        var frame = loadTennisMatchData(2013);

        frame.out().print();

        //Sort rows by row keys in descending order
        frame.rows().sort(false);
        //Print first ten rows
        frame.out().print(10);
    }


    @Test()
    public void sortColAxis() {
        var frame = loadTennisMatchData(2013);
        //Sort columns by column keys in ascending order
        frame.cols().sort(true);
        //Print first ten rows
        frame.out().print(10);
    }

    @Test()
    public void sortRowsByData1() {
        var frame = loadTennisMatchData(2013);
        //Sort rows by the WRank (winner rank) column values
        frame.rows().sort(true, "WRank");
        //Print first ten rows
        frame.out().print(10);
    }

    @Test()
    public void sortColsByKeys() {
        var frame = loadTennisMatchData(2013);
        //Sort columns by column keys in ascending order
        frame.cols().sort(true);
        //Print first ten rows
        frame.out().print(10);
    }

    @Test()
    public void sortColsByData1() {
        //Create a 10x10 frame initialized with random doubles
        var frame = DataFrame.ofDoubles(
            Range.of(0, 10).map(i -> "R" + i),
            Range.of(0, 10).map(i -> "C" + i),
            value -> Math.random() * 100d
        );
        //Sort columns by the data in first row
        frame.cols().sort(true, "R0");
        //Print first ten rows
        frame.out().print(10);
    }

    @Test()
    public void sortRowsAndColsByData1() {
        //Create a 10x10 frame initialized with random doubles
        var frame = DataFrame.ofDoubles(
            Range.of(0, 10).map(i -> "R" + i),
            Range.of(0, 10).map(i -> "C" + i),
            value -> Math.random() * 100d
        );
        //Sort columns by the data in first row
        frame.cols().sort(true, "R0");
        //Sort rows by the data that is now in the first column
        frame.rows().sort(true, frame.cols().key(0));
        //Print first ten rows
        frame.out().print(10);
    }


    @Test()
    public void sorRowsMultiDimensional() {
        var frame = loadTennisMatchData(2013);
        //Multidimensional row sort (ascending) first by Date, then WRank
        frame.rows().sort(true, Collect.asList("Date", "WRank"));
        //Print first ten rows
        frame.out().print(10);
    }


    @Test()
    public void sortRowsCustom() {
        var frame = loadTennisMatchData(2013);
        //Sort rows so that matches smallest difference in betting odds between winner and looser.
        frame.rows().sort((row1, row2) -> {
            double diff1 = Math.abs(row1.getDouble("AvgW") - row1.getDouble("AvgL"));
            double diff2 = Math.abs(row2.getDouble("AvgW") - row2.getDouble("AvgL"));
            return Double.compare(diff1, diff2);
        });
        //Print first ten rows
        frame.out().print(10);
    }

    @Test()
    public void sortOnFilter() {
        var frame = loadTennisMatchData(2013);
        //First filter frame to include only rows where Novak Djokovic was the victor
        var filter = frame.rows().select(row -> row.getValue("Winner").equals("Djokovic N."));
        //Sort rows so that the highest rank players he beat come first
        filter.rows().sort(true, "LRank");
        //Print first ten rows
        filter.out().print(10);
    }


    @Test()
    public void performanceSequential() throws Exception {

        //Define range of row counts we want to test, from 1M to 5M inclusive
        var rowCounts = Range.of(1, 6).map(i -> i * 1000000);

        //Time DataFrame sort operations on frame of random doubles with row counts ranging from 1M to 6M
        var results = DataFrame.combineFirst(rowCounts.map(rowCount -> {
            var rowKeys = Range.of(0, rowCount.intValue());
            var colKeys = Range.of(0, 5).map(i -> "C" + i);
            //Create frame initialized with random double values
            var frame = DataFrame.ofDoubles(rowKeys, colKeys, v -> Math.random() * 100d);
            var label = "Rows(" + (rowCount / 1000000) + "M)";
            //Run each test 10 times, clear the sort before running the test with sort(null)
            return PerfStat.run(10, TimeUnit.MILLISECONDS, false, tasks -> {
                tasks.beforeEach(() -> frame.rows().sort(null));
                tasks.put(label, () -> frame.rows().sort(true, "C1"));
            });
        }));

        //Plot the results of the combined DataFrame with timings
        Chart.create().withBarPlot(results, false, chart -> {
            chart.plot().axes().domain().label().withText("Timing Statistic");
            chart.plot().axes().range(0).label().withText("Time In Milliseconds");
            chart.title().withText("DataFrame Sorting Performance (Sequential)");
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.subtitle().withText("Row Sort with counts from 1M to 5M rows");
            chart.legend().on().bottom();
            chart.writerPng(new File("./docs/images/frame/data-frame-row-sort-sequential.png"), 845, 400, true);
            chart.show();
        });
    }



    @Test()
    public void performanceParallel() throws Exception {

        //Define range of row counts we want to test, from 1M to 5M inclusive
        var rowCounts = Range.of(1, 6).map(i -> i * 1000000);

        //Time DataFrame sort operations on frame of random doubles with row counts ranging from 1M to 6M
        var results = DataFrame.combineFirst(rowCounts.map(rowCount -> {
            var rowKeys = Range.of(0, rowCount.intValue());
            var colKeys = Range.of(0, 5).map(i -> "C" + i);
            //Create frame initialized with random double values
            var frame = DataFrame.ofDoubles(rowKeys, colKeys, v -> Math.random() * 100d);
            var label = "Rows(" + (rowCount / 1000000) + "M)";
            //Run each test 10 times, clear the sort before running the test with sort(null)
            return PerfStat.run(10, TimeUnit.MILLISECONDS, false, tasks -> {
                tasks.beforeEach(() -> frame.rows().sort(null));
                tasks.put(label, () -> frame.rows().parallel().sort(true, "C1"));
            });
        }));

        //Plot the results of the combined DataFrame with timings
        Chart.create().withBarPlot(results, false, chart -> {
            chart.plot().axes().domain().label().withText("Timing Statistic");
            chart.plot().axes().range(0).label().withText("Time In Milliseconds");
            chart.title().withText("DataFrame Sorting Performance (Parallel)");
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.subtitle().withText("Row Sort with counts from 1M to 5M rows");
            chart.legend().on().bottom();
            chart.writerPng(new File("./docs/images/frame/data-frame-row-sort-parallel.png"), 845, 400, true);
            chart.show();
        });
    }

    @Test()
    public void performanceComparator() {

        //Create frame initialized with random double values
        var rowKeys = Range.of(0, 1000000);
        var colKeys = Range.of(0, 5).map(i -> "C" + i);
        var frame = DataFrame.ofDoubles(rowKeys, colKeys, v -> Math.random() * 100d);
        //Define comparator to sort rows by column C1, which is ordinal 1
        Comparator<DataFrameRow<Integer,String>> comparator = (row1, row2) -> {
            double v1 = row1.getDoubleAt(1);
            double v2 = row2.getDoubleAt(1);
            return Double.compare(v1, v2);
        };

        //Time sorting in various modes (with & without comparator in both sequential & parallel mode)
        var results = PerfStat.run(10, TimeUnit.MILLISECONDS, false, tasks -> {
            tasks.beforeEach(() -> frame.rows().sort(null));
            tasks.put("W/O Comparator (seq)", () -> frame.rows().sort(true, "C1"));
            tasks.put("W/O Comparator (par)", () -> frame.rows().parallel().sort(true, "C1"));
            tasks.put("W/ Comparator (seq)", () -> frame.rows().sort(comparator));
            tasks.put("W/ Comparator (par)", () -> frame.rows().parallel().sort(comparator));
        });

        //Plot the results of the combined DataFrame with timings
        Chart.create().withBarPlot(results, false, chart -> {
            chart.plot().axes().domain().label().withText("Timing Statistic");
            chart.plot().axes().range(0).label().withText("Time In Milliseconds");
            chart.title().withText("DataFrame Sorting Performance With & Without Comparator");
            chart.subtitle().withText("1 Million rows of random double precision values");
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.legend().on().bottom();
            chart.writerPng(new File("./docs/images/frame/data-frame-row-sort-comparator.png"), 845, 400, true);
            chart.show();
        });

    }

}
