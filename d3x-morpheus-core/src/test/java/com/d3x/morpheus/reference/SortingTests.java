/*
 * Copyright (C) 2014-2017 Xavier Witdouck
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
package com.d3x.morpheus.reference;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayType;
import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.frame.DataFrameAsserts;
import com.d3x.morpheus.index.Index;
import com.d3x.morpheus.range.Range;
import com.d3x.morpheus.util.IntComparator;
import com.d3x.morpheus.util.SortAlgorithm;
import com.d3x.morpheus.util.Swapper;

/**
 * A test for DataFrame sorting functionality
 *
 * @author  Xavier Witdouck
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 */
public class SortingTests {


    @DataProvider(name="order")
    public Object[][] getArgs0() {
        return new Object[][] {
            { true, false },
            { true, true },
            { false, false },
            { false, true },
        };
    }


    @DataProvider(name="args1")
    public Object[][] getArgs1() {
        return new Object[][] {
            { false },
            { true },
            { false },
            { true },
        };
    }


    @DataProvider(name="args2")
    public Object[][] getArgs2() {
        return new Object[][] {
            { boolean.class, false },
            { boolean.class, true },
            { int.class, false },
            { int.class, true },
            { long.class, false },
            { long.class, true },
            { double.class, false },
            { double.class, true },
            { String.class, false },
            { String.class, true },
        };
    }


    @SuppressWarnings("unchecked")
    private static Comparator<Object> comparator = (v1, v2) -> {
        if (v1 != null && v2 != null) {
            final Comparable c1 = (Comparable)v1;
            final Comparable c2 = (Comparable)v2;
            return c1.compareTo(c2);
        } else if (v1 == null && v2 == null) {
            return 0;
        } else if (v1 == null) {
            return -1;
        } else {
            return 1;
        }
    };


    @Test()
    public void testSortingByRowsAscending() throws Exception {
        var frame = TestDataFrames.getQuotes("blk");
        var copy = frame.copy();
        DataFrameAsserts.assertEqualsByIndex(copy, frame);
        var sorted = copy.rows().sort(true, "Close");
        sorted.rows().ordinals().forEach(i -> {
            if (i > 0) {
                var date = copy.rows().key(i);
                var value0 = sorted.col("Close").getDoubleAt(i - 1);
                var value1 = sorted.col("Close").getDoubleAt(i);
                Assert.assertTrue(value0 <= value1, "Close: " + value0 + " <= " + value1 + " at " + date);
            }
        });
        DataFrameAsserts.assertEqualsByKey(copy, frame);
    }

    @Test()
    public void testSortingByRowsDescending() throws Exception {
        var frame = TestDataFrames.getQuotes("blk");
        var sorted = frame.rows().sort(false, "Close");
        sorted.rows().ordinals().forEach(i -> {
            if (i > 0) {
                var date = frame.rows().key(i);
                var value0 = sorted.col("Close").getDoubleAt(i - 1);
                var value1 = sorted.col("Close").getDoubleAt(i);
                Assert.assertTrue(value1 <= value0, "Close at " + date + " is <= prior value");
            }
        });
        DataFrameAsserts.assertEqualsByKey(sorted, frame);
    }

    @Test()
    public void testSortingByRowKeyAscending() throws Exception {
        var frame = TestDataFrames.getQuotes("blk");
        var sorted = frame.rows().sort((row0, row1) -> row0.key().compareTo(row1.key()));
        sorted.rows().ordinals().forEach(i -> {
            if (i > 0) {
                var value0 = sorted.rows().key(i - 1);
                var value1 = sorted.rows().key(i);
                Assert.assertTrue(value0.compareTo(value1) <= 0, "Date at " + value0 + "  <= " + value1 + " prior value");
            }
        });
        DataFrameAsserts.assertEqualsByKey(sorted, frame);
    }

    @Test()
    public void testSortByRowKeyDescending() throws Exception {
        final DataFrame<LocalDate,String> frame = TestDataFrames.getQuotes("blk");
        final DataFrame<LocalDate,String> copy = frame.copy();
        DataFrameAsserts.assertEqualsByIndex(copy, frame);
        DataFrame<LocalDate,String> sorted = copy.rows().sort((row0, row1) -> row1.key().compareTo(row0.key()));
        sorted.out().print();
        sorted.rows().forEach(row -> {
            if (row.ordinal() > 0) {
                final LocalDate value0 = sorted.rows().key(row.ordinal()-1);
                final LocalDate value1 = sorted.rows().key(row.ordinal());
                Assert.assertTrue(value0.compareTo(value1) >= 0, "Date at " + value0 + "  >= " + value1 + " prior value");
            }
        });
        DataFrameAsserts.assertEqualsByKey(copy, frame);
    }


    @Test(dataProvider="args1")
    public void testSortByColumnsAscending(boolean parallel) throws Exception {
        final DataFrame<String,String> frame = TestDataFrames.random(double.class, 100, 100).applyDoubles(v -> Math.random() * 100);
        final DataFrame<String,String> copy = frame.copy();
        final String rowKey = copy.rows().key(23);
        DataFrameAsserts.assertEqualsByIndex(copy, frame);
        final DataFrame<String,String> sorted = parallel ? copy.parallel().cols().sort(true, rowKey) : copy.sequential().cols().sort(true, rowKey);
        sorted.cols().ordinals().forEach(colIndex -> {
            if (colIndex > 0) {
                final double value0 = sorted.row(rowKey).getDoubleAt(colIndex - 1);
                final double value1 = sorted.row(rowKey).getDoubleAt(colIndex);
                Assert.assertTrue(value0 <= value1, "Entry at " + rowKey + " is <= prior value");
            }
        });
        DataFrameAsserts.assertEqualsByKey(copy, frame);
    }


    @Test(dataProvider="args1")
    public void testSortingByColumnsDescending(boolean parallel) throws Exception {
        final DataFrame<String,String> frame = TestDataFrames.random(double.class, 100, 100).applyDoubles(v -> Math.random() * 100);
        final DataFrame<String,String> copy = frame.copy();
        final String rowKey = copy.rows().key(23);
        DataFrameAsserts.assertEqualsByIndex(copy, frame);
        DataFrame<String,String> sorted = parallel ? copy.cols().parallel().sort(false, rowKey) : copy.cols().sequential().sort(false, rowKey);
        sorted.cols().ordinals().forEach(colIndex -> {
            if (colIndex > 0) {
                final double value0 = sorted.row(rowKey).getDoubleAt(colIndex - 1);
                final double value1 = sorted.row(rowKey).getDoubleAt(colIndex);
                Assert.assertTrue(value0 >= value1, "Entry at " + rowKey + " is >= prior value");
            }
        });
        DataFrameAsserts.assertEqualsByKey(copy, frame);
    }


    @Test()
    public void testSortingByColumnKeysAscending() throws Exception {
        final DataFrame<LocalDate,String> frame = TestDataFrames.getQuotes("blk");
        final List<String> columns = frame.cols().keys().collect(Collectors.toList());
        Collections.sort(columns);
        frame.cols().sort((col1, col2) -> col1.key().compareTo(col2.key()));
        frame.cols().ordinals().forEach(i -> frame.cols().key(i).equals(columns.get(i)));
    }

    @Test()
    public void testSortingByColumnKeysDescending() throws Exception {
        final DataFrame<LocalDate,String> frame = TestDataFrames.getQuotes("blk");
        final List<String> columns = frame.cols().keys().collect(Collectors.toList());
        Collections.sort(columns);
        Collections.reverse(columns);
        frame.cols().sort((col1, col2) -> col2.key().compareTo(col1.key()));
        frame.cols().ordinals().forEach(i -> frame.cols().key(i).equals(columns.get(i)));
    }

    @Test()
    public void testSortRowsAndColumns1() throws Exception {
        final DataFrame<LocalDate,String> frame = TestDataFrames.getQuotes("blk");
        final DataFrame<LocalDate,String> sorted = frame.copy();
        sorted.rows().sort(false, "Close");
        sorted.cols().sort((col0, col1) -> col0.key().compareTo(col1.key()));
        for (int i=0; i<frame.rowCount(); ++i) {
            for (int j = 0; j<frame.colCount(); ++j) {
                final LocalDate date = frame.rows().key(i);
                final String column = frame.cols().key(j);
                final Object left = frame.getValue(date, column);
                final Object right = sorted.getValue(date, column);
                Assert.assertEquals(left, right, "Values equal at (" + date + "," + column + ")");
                Assert.assertNotSame(sorted.getValueAt(i,j), frame.getValueAt(i,j));
            }
        }
    }

    @Test()
    public void testSortRowsAndColumns2() throws Exception {
        final DataFrame<LocalDate,String> frame = TestDataFrames.getQuotes("blk");
        final DataFrame<LocalDate,String> sorted = frame.copy();
        sorted.rows().sort(false, "Volume");
        sorted.cols().sort((col0, col1) -> col0.key().compareTo(col1.key()));
        for (int i=0; i<frame.rowCount(); ++i) {
            for (int j = 0; j<frame.colCount(); ++j) {
                final LocalDate date = frame.rows().key(i);
                final String column = frame.cols().key(j);
                var rowIndexLeft = frame.rows().ordinal(date);
                var colIndexLeft = frame.cols().ordinal(column);
                var rowIndexRight = sorted.rows().ordinal(date);
                var colIndexRight = sorted.cols().ordinal(column);
                final Object left = frame.getValueAt(rowIndexLeft, colIndexLeft);
                final Object right = sorted.getValueAt(rowIndexRight, colIndexRight);
                Assert.assertEquals(left, right, "Values equal at (" + date + "," + column + ")");
            }
        }
        sorted.rows().sort(null);
        sorted.cols().sort(null);
        for (int i=0; i<frame.rowCount(); ++i) {
            for (int j = 0; j<frame.colCount(); ++j) {
                final LocalDate date = frame.rows().key(i);
                final String column = frame.cols().key(j);
                final Object left = frame.getValueAt(i,j);
                final Object right = sorted.getValueAt(i,j);
                Assert.assertEquals(left, right, "Values equal at (" + date + "," + column + ")");
            }
        }
    }


    @Test()
    public void testSortColumns() throws Exception {
        final Index<LocalDate> rowKeys = Index.of(LocalDate.class, 100);
        final Index<String> colKeys = Index.ofObjects("AAPL", "ORCL", "GOOGL", "BLK", "YHOO");
        final DataFrame<LocalDate,String> frame = DataFrame.ofDoubles(rowKeys, colKeys);
        frame.rows().add(LocalDate.of(2013, 6, 2), v -> 10d * Math.random());
        frame.rows().add(LocalDate.of(2013, 6, 3), v -> 10d * Math.random());
        frame.rows().add(LocalDate.of(2013, 6, 4), v -> 10d * Math.random());
        frame.rows().add(LocalDate.of(2013, 6, 5), v -> 10d * Math.random());
        frame.rows().add(LocalDate.of(2013, 6, 6), v -> 10d * Math.random());
        frame.rows().add(LocalDate.of(2013, 6, 7), v -> 10d * Math.random());
        final DataFrame<LocalDate,String> sorted = frame.cols().sort(true, LocalDate.of(2013, 6, 4));
        sorted.out().print();
        for (int j = 1; j<frame.colCount(); ++j) {
            final double value0 = sorted.row(LocalDate.of(2013, 6, 4)).getDoubleAt(j-1);
            final double value1 = sorted.row(LocalDate.of(2013, 6, 4)).getDoubleAt(j);
            Assert.assertTrue(value0 <= value1, "Value " + value0 + " <= " + value1);
        }
    }


    @Test()
    public void testSortLargeFrame() throws Exception {
        var size = 5000000;
        final long t1 = System.nanoTime();
        final Range<Integer> rows = Range.of(0, size);
        final Range<Integer> columns = Range.of(0, 10);
        final DataFrame<Integer,Integer> frame = DataFrame.ofDoubles(rows, columns);
        final long t2 = System.nanoTime();
        System.out.println("Created DataFrame in " + ((t2 - t1)/1000000) + " millis");
        frame.applyDoubles(v -> 100d * Math.random());
        final long t3 = System.nanoTime();
        frame.rows().parallel().sort(true, 5);
        final long t4 = System.nanoTime();
        System.out.println("Sorted rows in " + ((t4-t3)/1000000) + " millis");
    }


    @Test()
    public void testSortBasic() {
        var size = 5000000;
        var modelIndexes = IntStream.range(0, size).toArray();
        final double[] values = IntStream.range(0, size).mapToDouble(i -> Math.random()).toArray();
        final IntComparator comparator = (int viewIndex1, int viewIndex2) -> {
            var modelIndex1 = modelIndexes[viewIndex1];
            var modelIndex2 = modelIndexes[viewIndex2];
            final double v1 = values[modelIndex1];
            final double v2 = values[modelIndex2];
            return Double.compare(v1, v2);
        };
        final Swapper swapper = (int viewIndex1, int viewIndex2) -> {
            var modelIndex1 = modelIndexes[viewIndex1];
            var modelIndex2 = modelIndexes[viewIndex2];
            modelIndexes[viewIndex1] = modelIndex2;
            modelIndexes[viewIndex2] = modelIndex1;
        };
        final long t1 = System.currentTimeMillis();
        SortAlgorithm.getDefault(false).sort(0, size, comparator, swapper);
        final long t2 = System.currentTimeMillis();
        System.out.println("Sorted double array in " + (t2-t1) + " millis");
        for (int i=1; i<modelIndexes.length; ++i) {
            final double x = values[modelIndexes[i-1]];
            final double y = values[modelIndexes[i]];
            Assert.assertTrue(x <= y, "Index " + i);
        }
    }


    @Test(dataProvider="args1")
    public void testMultiDimensionalRowSort1(boolean parallel) {
        final List<String> colKeys = Arrays.asList("Booleans", "Integers", "Longs", "Doubles", "Dates");
        final DataFrame<LocalDate,String> frame = createRowTestFrame(parallel, 10000).rows().sort(true, colKeys);
        frame.out().print();
        for (int i=1; i<frame.rowCount(); ++i) {
            Assert.assertTrue(Boolean.compare(frame.col("Booleans").getBooleanAt(i-1), frame.col("Booleans").getBooleanAt(i)) <= 0, "Booleans are sorted");
        }
        final boolean[] booleanValues = new boolean[] { true, false };
        for (boolean booleanValue : booleanValues) {
            final DataFrame<LocalDate,String> df1 = frame.rows().select(row -> row.getBoolean("Booleans") == booleanValue);
            Assert.assertTrue(df1.rowCount() > 0, "There is at least one row with boolean = " + booleanValue);
            for (int j=1; j<df1.rowCount(); ++j) {
                var int1 = df1.col("Integers").getIntAt(j - 1);
                var int2 = df1.col("Integers").getIntAt(j);
                Assert.assertTrue(Integer.compare(int1, int2) <= 0, "Integers are sorted for boolean=" + booleanValue + ": " + int1 + " > " + int2);
            }
            var intValues = df1.col("Integers").toIntStream().distinct().toArray();
            for (int intValue: intValues) {
                final DataFrame<LocalDate,String> df2 = df1.rows().select(row -> row.getInt("Integers") == intValue);
                Assert.assertTrue(df2.rowCount() > 0, "There is at least one row with value = " + intValue);
                for (int j=1; j<df2.rowCount(); ++j) {
                    final long long1 = df2.col("Longs").getLongAt(j - 1);
                    final long long2 = df2.col("Longs").getLongAt(j);
                    Assert.assertTrue(Long.compare(long1, long2) <= 0, "Longs are sorted for int=" + intValue);
                }
                final long[] longValues = df2.col("Longs").toLongStream().distinct().toArray();
                for (long longValue: longValues) {
                    final DataFrame<LocalDate,String> df3 = df2.rows().select(row -> row.getLong("Longs") == longValue);
                    Assert.assertTrue(df3.rowCount() > 0, "There is at least one row with value = " + longValue);
                    for (int j=1; j<df3.rowCount(); ++j) {
                        final double v1 = df3.col("Doubles").getDoubleAt(j - 1);
                        final double v2 = df3.col("Doubles").getDoubleAt(j);
                        Assert.assertTrue(Double.compare(v1, v2) <= 0, "Doubles are sorted for long=" + longValue);
                    }
                    final double[] doubleValues = df3.col("Doubles").toDoubleStream().distinct().toArray();
                    for (double doubleValue: doubleValues) {
                        final DataFrame<LocalDate,String> df4 = df3.rows().select(row -> row.getDouble("Doubles") == doubleValue);
                        Assert.assertTrue(df4.rowCount() > 0, "There is at least one row with value = " + doubleValue);
                        for (int j=1; j<df4.rowCount(); ++j) {
                            final LocalDate v1 = df3.col("Dates").getValueAt(j - 1);
                            final LocalDate v2 = df3.col("Dates").getValueAt(j);
                            Assert.assertTrue(v1.compareTo(v2) <= 0, "Dates are sorted for double=" + doubleValue);
                        }
                    }
                }
            }
        }
    }


    @Test(dataProvider="args1")
    public void testMultiDimensionalRowSort2(boolean parallel) {
        var frame = createRowTestFrame(parallel, 10000).rows().sort((row1, row2) -> {
            var b1 = row1.getBoolean("Booleans");
            var b2 = row2.getBoolean("Booleans");
            if (Boolean.compare(b1, b2) != 0) {
                return Boolean.compare(b1, b2);
            } else {
                var i1 = row1.getInt("Integers");
                var i2 = row2.getInt("Integers");
                if (i1 != i2) {
                    return Integer.compare(i1, i2);
                } else {
                    var l1 = row1.getLong("Longs");
                    var l2 = row2.getLong("Longs");
                    if (l1 != l2) {
                        return Long.compare(l1, l2);
                    } else {
                        var d1 = row1.getDouble("Doubles");
                        var d2 = row2.getDouble("Doubles");
                        if (Double.compare(d1,d2) != 0) {
                            return Double.compare(d1, d2);
                        } else {
                            var v1 = (LocalDate)row1.getValue("Dates");
                            var v2 = (LocalDate)row2.getValue("Dates");
                            if (v1.compareTo(v2) != 0) {
                                return v1.compareTo(v2);
                            } else {
                                return 0;
                            }
                        }
                    }
                }
            }
        });
        frame.out().print();
        for (int i=1; i<frame.rowCount(); ++i) {
            var b1 = frame.col("Booleans").getBooleanAt(i-1);
            var b2 = frame.col("Booleans").getBooleanAt(i);
            var comp = Boolean.compare(b1, b2);
            Assert.assertTrue(comp <= 0, "Not sorted at index: " + i);
        }
        final boolean[] booleanValues = new boolean[] { true, false };
        for (boolean booleanValue : booleanValues) {
            final DataFrame<LocalDate,String> df1 = frame.rows().select(row -> row.getBoolean("Booleans") == booleanValue);
            Assert.assertTrue(df1.rowCount() > 0, "There is at least one row with boolean = " + booleanValue);
            for (int j=1; j<df1.rowCount(); ++j) {
                var int1 = df1.col("Integers").getIntAt(j - 1);
                var int2 = df1.col("Integers").getIntAt(j);
                Assert.assertTrue(Integer.compare(int1, int2) <= 0, "Integers are sorted for boolean=" + booleanValue);
            }
            var intValues = df1.col("Integers").toIntStream().distinct().toArray();
            for (int intValue: intValues) {
                final DataFrame<LocalDate,String> df2 = df1.rows().select(row -> row.getInt("Integers") == intValue);
                Assert.assertTrue(df2.rowCount() > 0, "There is at least one row with value = " + intValue);
                for (int j=1; j<df2.rowCount(); ++j) {
                    final long long1 = df2.col("Longs").getLongAt(j - 1);
                    final long long2 = df2.col("Longs").getLongAt(j);
                    Assert.assertTrue(Long.compare(long1, long2) <= 0, "Longs are sorted for int=" + intValue);
                }
                final long[] longValues = df2.col("Longs").toLongStream().distinct().toArray();
                for (long longValue: longValues) {
                    final DataFrame<LocalDate,String> df3 = df2.rows().select(row -> row.getLong("Longs") == longValue);
                    Assert.assertTrue(df3.rowCount() > 0, "There is at least one row with value = " + longValue);
                    for (int j=1; j<df3.rowCount(); ++j) {
                        final double v1 = df3.col("Doubles").getDoubleAt(j - 1);
                        final double v2 = df3.col("Doubles").getDoubleAt(j);
                        Assert.assertTrue(Double.compare(v1, v2) <= 0, "Doubles are sorted for long=" + longValue);
                    }
                    final double[] doubleValues = df3.col("Doubles").toDoubleStream().distinct().toArray();
                    for (double doubleValue: doubleValues) {
                        final DataFrame<LocalDate,String> df4 = df3.rows().select(row -> row.getDouble("Doubles") == doubleValue);
                        Assert.assertTrue(df4.rowCount() > 0, "There is at least one row with value = " + doubleValue);
                        for (int j=1; j<df4.rowCount(); ++j) {
                            final LocalDate v1 = df3.col("Dates").getValueAt(j - 1);
                            final LocalDate v2 = df3.col("Dates").getValueAt(j);
                            Assert.assertTrue(v1.compareTo(v2) <= 0, "Dates are sorted for double=" + doubleValue);
                        }
                    }
                }
            }
        }
    }


    @Test(dataProvider="args2")
    @SuppressWarnings("unchecked")
    public void testMultidimensionalColumnSort1(Class type, boolean parallel) {
        final DataFrame<String,LocalDate> frame = createColumnTestFrame(type, parallel, 1000);
        final DataFrame<String,LocalDate> copy = frame.copy();
        final String rowKey1 = frame.rows().key(2);
        final String rowKey2 = frame.rows().key(4);
        final AtomicInteger firstCount = new AtomicInteger();
        final AtomicInteger secondCount = new AtomicInteger();
        final DataFrame<String,LocalDate> sorted = frame.cols().sort(true, Arrays.asList(rowKey1, rowKey2));
        DataFrameAsserts.assertEqualsByKey(copy, frame);
        sorted.out().print();
        sorted.cols().ordinals().forEach(colIndex -> {
            if (colIndex > 0) {
                final Comparable v1 = sorted.row(rowKey1).getValueAt(colIndex - 1);
                final Comparable v2 = sorted.row(rowKey1).getValueAt(colIndex);
                Assert.assertTrue(comparator.compare(v1, v2) <= 0, "First dimension sort okay");
                firstCount.incrementAndGet();
                if (comparator.compare(v1, v2) == 0) {
                    final Comparable v3 = sorted.row(rowKey2).getValueAt(colIndex - 1);
                    final Comparable v4 = sorted.row(rowKey2).getValueAt(colIndex);
                    Assert.assertTrue(v3 == null || v3.compareTo(v4) <= 0, "Second dimension sort okay");
                    secondCount.incrementAndGet();
                }
            }
        });
        Assert.assertTrue(firstCount.get() > 0, "There was at least one hit on first dimension");
        Assert.assertTrue(secondCount.get() > 0, "There was at least one hit on second dimension");
    }


    @Test(dataProvider = "order")
    public void testIntegerRowSortByKeys(boolean ascending, boolean parallel) {
        final Random random = new Random();
        final Array<Integer> rowKeys = Range.of(0, 10000).toArray().shuffle(2);
        final Array<String> colKeys = Array.ofObjects("A", "B", "C", "D");
        final DataFrame<Integer,String> frame = DataFrame.ofDoubles(rowKeys, colKeys).applyDoubles(v -> random.nextDouble());
        frame.out().print();
        final DataFrame<Integer,String> sorted = parallel ? frame.rows().parallel().sort(ascending) : frame.rows().sequential().sort(ascending);
        sorted.out().print();
        sorted.rows().forEach(row -> {
            if (row.ordinal() > 0) {
                var key0 = sorted.rows().key(row.ordinal()-1);
                var key1 = sorted.rows().key(row.ordinal());
                if (ascending) {
                    Assert.assertTrue(key0.compareTo(key1) < 0, "Keys are in ascending order");
                } else {
                    Assert.assertTrue(key0.compareTo(key1) > 0, "Keys are in descending order");
                }
            }
        });
    }


    @Test(dataProvider = "order")
    public void testLocalDateRowSortByKeys(boolean ascending, boolean parallel) {
        final Random random = new Random();
        final Array<LocalDate> rowKeys = Range.ofLocalDates("2000-01-01", "2010-01-01").toArray().shuffle(2);
        final Array<String> colKeys = Array.ofObjects("A", "B", "C", "D");
        final DataFrame<LocalDate,String> frame = DataFrame.ofDoubles(rowKeys, colKeys).applyDoubles(v -> random.nextDouble());
        frame.out().print();
        final DataFrame<LocalDate,String> sorted = parallel ? frame.rows().parallel().sort(ascending) : frame.rows().sequential().sort(ascending);
        sorted.out().print();
        sorted.rows().forEach(row -> {
            if (row.ordinal() > 0) {
                final LocalDate key0 = sorted.rows().key(row.ordinal()-1);
                final LocalDate key1 = sorted.rows().key(row.ordinal());
                if (ascending) {
                    Assert.assertTrue(key0.compareTo(key1) < 0, "Keys are in ascending order");
                } else {
                    Assert.assertTrue(key0.compareTo(key1) > 0, "Keys are in descending order");
                }
            }
        });
    }



    /**
     * Returns a frame for sort testing containing all data types
     * @param parallel      true for parallel version
     * @param rowCount      the row count
     * @return              the newly created DataFrame
     */
    private DataFrame<LocalDate,String> createRowTestFrame(boolean parallel, int rowCount) {
        final Random random = new Random();
        final LocalDate startDate = LocalDate.of(1990,1,1);
        final Index<LocalDate> rowKeys = Index.of(LocalDate.class, rowCount);
        final Index<String> colKeys = Index.of(String.class, 10);
        final DataFrame<LocalDate,String> frame = DataFrame.ofObjects(rowKeys, colKeys);
        frame.rows().addAll(Range.of(0, rowCount).map(startDate::plusDays));
        frame.cols().add("Booleans", Array.of(Boolean.class, rowCount));
        frame.cols().add("Integers", Array.of(Integer.class, rowCount));
        frame.cols().add("Longs", Array.of(Long.class, rowCount));
        frame.cols().add("Doubles", Array.of(Double.class, rowCount));
        frame.cols().add("Strings", Array.of(String.class, rowCount));
        frame.cols().add("Dates", Array.of(LocalDate.class, rowCount));

        boolean booleanValue = random.nextBoolean();
        int intValue = random.nextInt();
        long longValue = random.nextLong();
        double doubleValue = random.nextDouble();
        String stringValue = "XYZ-" + random.nextDouble();
        LocalDate dateValue = startDate.plusDays(random.nextInt(500));

        for (int rowIndex=0; rowIndex<rowCount; ++rowIndex) {
            frame.setBooleanAt(rowIndex, 0, booleanValue);
            frame.setIntAt(rowIndex, 1, intValue);
            frame.setLongAt(rowIndex, 2, longValue);
            frame.setDoubleAt(rowIndex, 3, doubleValue);
            frame.setValueAt(rowIndex, 4, stringValue);
            frame.setValueAt(rowIndex, 5, dateValue);
            if (rowIndex % 60 == 0) booleanValue = random.nextBoolean();
            if (rowIndex % 50 == 0) intValue = random.nextInt();
            if (rowCount % 40 == 0) longValue = random.nextLong();
            if (rowCount % 30 == 0) doubleValue = random.nextDouble();
            if (rowCount % 20 == 0) stringValue = "XYZ-" + random.nextDouble();
            if (rowCount % 10 == 0) dateValue = startDate.plusDays(random.nextInt(500));
        }
        return parallel ? frame.parallel() : frame.sequential();
    }


    /**
     * Returns a test frame for column sort functions
     * @param type      the data type for frame
     * @param parallel  true for parallel frame
     * @param colCount  the column count
     * @return          the newly created test frame
     */
    private DataFrame<String,LocalDate> createColumnTestFrame(Class type, boolean parallel, int colCount) {
        var rowCount = 10;
        var intervalSize = 5;
        final Random random = new Random();
        final LocalDate startDate = LocalDate.of(1990,1,1);
        final DataFrame<String,LocalDate> frame = DataFrame.of(String.class, LocalDate.class);
        final Range<LocalDate> columns = Range.of(0, colCount).map(startDate::plusDays);
        frame.rows().addAll(Range.of(0, rowCount).map(i -> "Row-" + i));
        switch (ArrayType.of(type)) {
            case BOOLEAN:
                frame.cols().addAll(columns, type);
                IntStream.range(0, (colCount - 1) / intervalSize).forEach(i -> {
                    final LocalDate colKey = frame.cols().key(i * intervalSize);
                    frame.col(colKey).applyBooleans(v -> random.nextBoolean());
                });
                frame.fill().right(100);
                break;
            case INTEGER:
                frame.cols().addAll(columns, type);
                IntStream.range(0, (colCount - 1) / intervalSize).forEach(i -> {
                    final LocalDate colKey = frame.cols().key(i * intervalSize);
                    frame.col(colKey).applyInts(v -> random.nextInt());
                });
                frame.fill().right(100);
                break;
            case LONG:
                frame.cols().addAll(columns, type);
                IntStream.range(0, (colCount - 1) / intervalSize).forEach(i -> {
                    final LocalDate colKey = frame.cols().key(i * intervalSize);
                    frame.col(colKey).applyLongs(v -> random.nextLong());
                });
                frame.fill().right(100);
                break;
            case DOUBLE:
                frame.cols().addAll(columns, type);
                IntStream.range(0, (colCount - 1) / intervalSize).forEach(i -> {
                    final LocalDate colKey = frame.cols().key(i * intervalSize);
                    frame.col(colKey).applyDoubles(v -> random.nextDouble());
                });
                frame.fill().right(100);
                break;
            case STRING:
                frame.cols().addAll(columns, type);
                IntStream.range(0, (colCount - 1) / intervalSize).forEach(i -> {
                    final LocalDate colKey = frame.cols().key(i * intervalSize);
                    frame.col(colKey).applyValues(v -> "X:" + random.nextDouble());
                });
                frame.fill().right(100);
                break;
            case OBJECT:
                frame.cols().addAll(columns, type);
                IntStream.range(0, (colCount - 1) / intervalSize).forEach(i -> {
                    final LocalDate colKey = frame.cols().key(i * intervalSize);
                    frame.col(colKey).applyValues(v -> "X:" + random.nextDouble());
                });
                frame.fill().right(100);
                break;
        }
        return parallel ? frame.parallel() : frame.sequential();
    }


    @Test()
    public void sortFilter1() {
        var rows = Range.of(0, 1000).map(i -> "R" + i);
        var cols = Range.of(0, 10).map(i -> "C" + i);
        var frame = DataFrame.ofDoubles(rows, cols, v -> Math.random());
        var filter = frame.rows().select("R0", "R20", "R44", "R89", "R150");
        var sorted = filter.rows().sort(true, "C2");
        var cursor = sorted.cursor().col("C2");
        sorted.out().print();
        for (int i=1; i<sorted.rowCount(); ++i) {
            var v1 = cursor.rowAt(i-1).getDouble();
            var v2 = cursor.rowAt(i).getDouble();
            Assert.assertEquals(Double.compare(v1, v2), -1);
        }
    }


    @Test()
    public void sortFilter2() {
        var rows = Range.of(0, 1000).map(i -> "R" + i);
        var cols = Range.of(0, 100).map(i -> "C" + i);
        var frame = DataFrame.ofDoubles(rows, cols, v -> Math.random());
        var filter = frame.cols().select("C0", "C20", "C44", "C89", "C99");
        var sorted = filter.cols().sort(true, "R2");
        var cursor = sorted.cursor().row("R2");
        sorted.out().print();
        for (int i=1; i<sorted.colCount(); ++i) {
            var v1 = cursor.colAt(i-1).getDouble();
            var v2 = cursor.colAt(i).getDouble();
            Assert.assertEquals(Double.compare(v1, v2), -1);
        }
    }



    @Test()
    public void sortFilterTranspose1() {
        var rows = Range.of(0, 100).map(i -> "R" + i);
        var cols = Range.of(0, 100).map(i -> "C" + i);
        var frame = DataFrame.ofDoubles(rows, cols, v -> Math.random()).transpose();
        var filter = frame.rows().select("C0", "C20", "C44", "C89", "C99");
        var sorted = filter.rows().sort(true, "R2");
        var cursor = sorted.cursor().col("R2");
        sorted.out().print();
        for (int i=1; i<sorted.rowCount(); ++i) {
            var v1 = cursor.rowAt(i-1).getDouble();
            var v2 = cursor.rowAt(i).getDouble();
            Assert.assertEquals(Double.compare(v1, v2), -1);
        }
    }



    @Test()
    public void sortFilterTranspose2() {
        var rows = Range.of(0, 100).map(i -> "R" + i);
        var cols = Range.of(0, 100).map(i -> "C" + i);
        var frame = DataFrame.ofDoubles(rows, cols, v -> Math.random()).transpose();
        var filter = frame.cols().select("R0", "R20", "R44", "R89", "R99");
        var sorted = filter.cols().sort(true, "C2");
        var cursor = sorted.cursor().row("C2");
        sorted.out().print();
        for (int i=1; i<sorted.colCount(); ++i) {
            var v1 = cursor.colAt(i-1).getDouble();
            var v2 = cursor.colAt(i).getDouble();
            Assert.assertEquals(Double.compare(v1, v2), -1);
        }
    }



}
