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

package com.d3x.morpheus.docs.array;

import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.d3x.morpheus.viz.chart.Chart;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.stats.StatType;
import com.d3x.morpheus.util.PerfStat;

public class ArrayPerf3 {

    private static final Class[] types = new Class[] {
            int.class,
            long.class,
            double.class,
            Date.class,
            LocalDate.class,
            String.class,
            ZonedDateTime.class
    };

    public static void main(String[] args) {

        final int count = 5;
        final int size = 1000000;

        final Array<String> colKeys = Array.of("Native", "Morpheus");
        final List<String> rowKeys = Arrays.stream(types).map(Class::getSimpleName).collect(Collectors.toList());
        final DataFrame<String,String> memory = DataFrame.ofDoubles(rowKeys, colKeys);
        final DataFrame<String,String> runTimes = DataFrame.ofDoubles(rowKeys, colKeys);
        final DataFrame<String,String> totalTimes = DataFrame.ofDoubles(rowKeys, colKeys);
        Arrays.stream(types).forEach(type -> {
            for (int style : new int[] {0, 1}) {
                System.out.println("Running tests for " + type);
                final String key = type.getSimpleName();
                final Callable<Object> callable = createCallable(type, size, style);
                final PerfStat stats = PerfStat.run(key, count, TimeUnit.MILLISECONDS, callable);
                final double gcTime = stats.getGcTime(StatType.MEDIAN);
                final double runTime = stats.getCallTime(StatType.MEDIAN);
                runTimes.rows().setDouble(key, style, runTime);
                totalTimes.rows().setDouble(key, style, runTime +  gcTime);
                memory.rows().setDouble(key, style, stats.getUsedMemory(StatType.MEDIAN));
            }
        });

        Chart.create().withBarPlot(runTimes, false, chart -> {
            chart.title().withText("Array Initialization Times, 1 Million Entries (Sample " + count + ")");
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.plot().axes().domain().label().withText("Data Type");
            chart.plot().axes().range(0).label().withText("Time (Milliseconds)");
            chart.plot().orient().horizontal();
            chart.legend().on();
            chart.writerPng(new File("./docs/images/native-vs-morpheus-init-times.png"), 845, 345, true);
            chart.show();
        });

        Chart.create().withBarPlot(totalTimes, false, chart -> {
            chart.title().withText("Array Initialization + GC Times, 1 Million Entries (Sample " + count + ")");
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.plot().axes().domain().label().withText("Data Type");
            chart.plot().axes().range(0).label().withText("Time (Milliseconds)");
            chart.plot().orient().horizontal();
            chart.legend().on();
            chart.writerPng(new File("./docs/images/native-vs-morpheus-gc-times.png"), 845, 345, true);
            chart.show();
        });

        Chart.create().withBarPlot(memory, false, chart -> {
            chart.title().withText("Array Memory Usage, 1 Million Entries (Sample " + count + ")");
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.plot().axes().domain().label().withText("Data Type");
            chart.plot().axes().range(0).label().withText("Memory Usage (MB)");
            chart.legend().on();
            chart.plot().orient().horizontal();
            chart.writerPng(new File("./docs/images/native-vs-morpheus-memory.png"), 845, 345, true);
            chart.show();
        });
    }

    /**
     * Returns a newly created callable for the args specified
     * @param type  the array class type
     * @param size  the size of array
     * @param style the style
     * @return      the callable
     */
    private static Callable<Object> createCallable(Class<?> type, int size, int style) {
        switch (style) {
            case 0: return ArrayPerf1.createNativeCallable(type, size);
            case 1: return ArrayPerf2.createMorpheusCallable(type, size);
            default:    throw new IllegalArgumentException("Unsupported style specified: " + style);
        }
    }

}
