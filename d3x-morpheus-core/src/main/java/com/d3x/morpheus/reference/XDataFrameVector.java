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
package com.d3x.morpheus.reference;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBuilder;
import com.d3x.morpheus.array.ArrayType;
import com.d3x.morpheus.array.ArrayUtils;
import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.frame.DataFrameAxis.Type;
import com.d3x.morpheus.frame.DataFrameCursor;
import com.d3x.morpheus.frame.DataFrameException;
import com.d3x.morpheus.frame.DataFrameValue;
import com.d3x.morpheus.frame.DataFrameVector;
import com.d3x.morpheus.range.Range;
import com.d3x.morpheus.stats.Statistic1;
import com.d3x.morpheus.stats.Stats;
import com.d3x.morpheus.util.Asserts;
import com.d3x.morpheus.util.Bounds;
import com.d3x.morpheus.util.functions.ToBooleanFunction;

/**
 * A convenience abstract implementation of the DataFrameVector interface.
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
abstract class XDataFrameVector<X,Y,R,C,Z> implements DataFrameVector<X,Y,R,C,Z>, Serializable {

    private static final long serialVersionUID = 1L;

    private Type axisType;
    private boolean parallel;
    private XDataFrame<R,C> frame;
    private XDataFrameStats<R,C> stats;

    /**
     * Constructor
     * @param frame     the frame reference
     * @param row       true if this is a row, false for a column
     * @param parallel  true for a parallel implementation
     */
    @SuppressWarnings("unchecked")
    XDataFrameVector(XDataFrame<R,C> frame, boolean row, boolean parallel) {
        this.frame = frame;
        this.parallel = parallel;
        this.axisType = row ? Type.ROWS : Type.COLS;
        this.stats = new XDataFrameStats<>(true, this);
    }

    @Override()
    public boolean isRow() {
        return axisType == Type.ROWS;
    }

    @Override()
    public boolean isColumn() {
        return axisType == Type.COLS;
    }

    @Override()
    public boolean isParallel() {
        return parallel;
    }

    @Override()
    public final DataFrame<R,C> frame() {
        return frame;
    }

    @Override()
    public final Stats<Double> stats() {
        return stats;
    }

    @Override()
    public final int size() {
        return isRow() ? frame.colCount() : frame.rowCount();
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Stream<R> rowKeys() {
        return isRow() ? Stream.of((R)key()) : frame.rowKeys().keys();
    }

    @Override()
    @SuppressWarnings("unchecked")
    public final Stream<C> colKeys() {
        return isRow() ? frame.colKeys().keys() : Stream.of((C)key());
    }


    @Override
    public final boolean hasNulls() {
        for (DataFrameValue<R,C> value : this) {
            if (value.isNull()) {
                return true;
            }
        }
        return false;
    }


    @Override
    public int count(Predicate<DataFrameValue<R, C>> predicate) {
        final AtomicInteger count = new AtomicInteger();
        this.forEachValue(v -> {
            if (predicate.test(v)) {
                count.incrementAndGet();
            }
        });
        return count.get();
    }


    @Override()
    public final Z applyBooleans(ToBooleanFunction<DataFrameValue<R,C>> mapper) {
        return forEachValue(value -> {
            final boolean result = mapper.applyAsBoolean(value);
            value.setBoolean(result);
        });
    }


    @Override()
    public final Z applyInts(ToIntFunction<DataFrameValue<R,C>> mapper) {
        return forEachValue(value -> {
            var result = mapper.applyAsInt(value);
            value.setInt(result);
        });
    }


    @Override()
    public final Z applyLongs(ToLongFunction<DataFrameValue<R,C>> mapper) {
        return forEachValue(value -> {
            final long result = mapper.applyAsLong(value);
            value.setLong(result);
        });
    }


    @Override()
    public final Z applyDoubles(ToDoubleFunction<DataFrameValue<R,C>> mapper) {
        return forEachValue(value -> {
            var result = mapper.applyAsDouble(value);
            value.setDouble(result);
        });
    }


    @Override()
    public final Z applyValues(Function<DataFrameValue<R,C>,?> mapper) {
        return forEachValue(value -> {
            final Object result = mapper.apply(value);
            value.setValue(result);
        });
    }


    @Override
    @SuppressWarnings("unchecked")
    public final DataFrame<R,C> toDataFrame() {
        switch (axisType) {
            case ROWS:  return frame.rows().select((R)key()).copy();
            case COLS:  return frame.cols().select((C)key()).copy();
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }


    @Override
    public final DataFrame<Double,String> hist(int binCount) {
        Asserts.check(binCount > 0, "The bin count must be > 0");
        var minValue = stats().min();
        var maxValue = stats().max();
        var stepSize = (maxValue - minValue) / binCount;
        var rowKeys = Range.of(minValue, maxValue + stepSize, stepSize);
        var hist = DataFrame.ofInts(rowKeys, "Count");
        var cursor = hist.cursor().colAt(0);
        this.forEachValue(v -> {
            var value = v.getDouble();
            hist.rows().lowerKey(value).ifPresent(lowerKey -> {
                var count = cursor.row(lowerKey).getInt();
                cursor.setInt(count + 1);
            });
        });
        return hist;
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final <V> Optional<Bounds<V>> bounds() {
        if (frame.rowCount() == 0 || frame.colCount() == 0) {
            return Optional.empty();
        } else {
            final Class<?> type = dataClass();
            switch (ArrayType.of(type)) {
                case INTEGER:   return Bounds.ofInts(toIntStream()).map(v -> (Bounds<V>)v);
                case LONG:      return Bounds.ofLongs(toLongStream()).map(v -> (Bounds<V>)v);
                case DOUBLE:    return Bounds.ofDoubles(toDoubleStream()).map(v -> (Bounds<V>)v);
                default:        return Bounds.ofValues(toValueStream()).map(v -> (Bounds<V>)v);
            }
        }
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final <T> Optional<DataFrameValue<R,C>> binarySearch(T value) {
        return binarySearch(0, size(), value, (v1, v2) -> {
            try {
                if (v1 == v2) {
                    return 0;
                } else if (v1 != null && v2 == null) {
                    return 1;
                } else if (v1 == null) {
                    return -1;
                } else {
                    return ((Comparable)v1).compareTo(v2);
                }
            } catch (Exception ex) {
                throw new DataFrameException("Failed to compare values in binary search, left=" + v1 + ", right=" + v2, ex);
            }
        });
    }


    @Override()
    public final <T> Optional<DataFrameValue<R,C>> binarySearch(T value, Comparator<T> comparator) {
        return binarySearch(0, size(), value, comparator);
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final <T> Optional<DataFrameValue<R,C>> binarySearch(int offset, int length, T value, Comparator<T> comparator) {
        Asserts.check(offset >= 0, "The start offset must be >= 0");
        Asserts.check(offset + length <= size(), "The offset + length must be <= size()");
        Asserts.check(comparator != null, "The Comparator cannot be null");
        try {
            int low = offset;
            int high = offset + length - 1;
            while (low <= high) {
                var midIndex = (low + high) >>> 1;
                final T midValue = getValueAt(midIndex);
                var result = comparator.compare(midValue, value);
                if (result < 0) {
                    low = midIndex + 1;
                } else if (result > 0) {
                    high = midIndex - 1;
                } else if (isRow()) {
                    final R rowKey = (R)key();
                    final DataFrameCursor<R,C> cursor = frame.cursor();
                    return Optional.of(cursor.row(rowKey).colAt(midIndex));
                } else {
                    final C colKey = (C)key();
                    final DataFrameCursor<R,C> cursor = frame.cursor();
                    return Optional.of(cursor.rowAt(midIndex).col(colKey));
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            throw new DataFrameException("Binary search of of DataFrame vector failed for: " + key(), ex);
        }
    }

    @Override
    public final Optional<DataFrameValue<R,C>> min() {
        return min(v -> true);
    }


    @Override
    @SuppressWarnings("unchecked")
    public final Optional<DataFrameValue<R,C>> max() {
        return max(v -> true);
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final <V> Array<V> distinct() {
        return distinct(Integer.MAX_VALUE);
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final <V> Array<V> distinct(int limit) {
        if (limit == 0 || frame.rowCount() == 0 || frame.colCount() == 0) {
            return Array.empty((Class<V>)key().getClass());
        } else {
            final Class<?> type = dataClass();
            switch (ArrayType.of(type)) {
                case INTEGER:   return (Array<V>)ArrayUtils.distinct(toIntStream(), limit);
                case LONG:      return (Array<V>)ArrayUtils.distinct(toLongStream(), limit);
                case DOUBLE:    return (Array<V>)ArrayUtils.distinct(toDoubleStream(), limit);
                default:        return (Array<V>)ArrayUtils.distinct(toValueStream(), limit);
            }
        }
    }

    @Override
    public final IntStream toIntStream() {
        return values().mapToInt(DataFrameValue::getInt);
    }


    @Override
    public final LongStream toLongStream() {
        return values().mapToLong(DataFrameValue::getLong);
    }


    @Override
    public final DoubleStream toDoubleStream() {
        return values().mapToDouble(DataFrameValue::getDouble);
    }


    @Override
    @SuppressWarnings("unchecked")
    public final <V> Stream<V> toValueStream() {
        return values().map(DataFrameValue::getValue);
    }


    @Override
    @SuppressWarnings("unchecked")
    public final <V> Array<V> toArray() {
        var length = size();
        final Class<V> type = (Class<V>) dataClass();
        final ArrayBuilder<V> builder = ArrayBuilder.of(length, type);
        switch (ArrayType.of(type)) {
            case INTEGER:   forEach(v -> builder.appendInt(v.getInt()));       break;
            case LONG:      forEach(v -> builder.appendLong(v.getLong()));     break;
            case DOUBLE:    forEach(v -> builder.appendDouble(v.getDouble())); break;
            default:        forEach(v -> builder.append(v.getValue()));        break;
        }
        return builder.toArray();
    }


    @Override()
    public final Stream<DataFrameValue<R,C>> values() {
        var valueCount = size();
        if (valueCount == 0) {
            return Stream.empty();
        } else {
            var partitionSize = valueCount / Runtime.getRuntime().availableProcessors();
            var splitThreshold = Math.max(partitionSize, 5000);
            return StreamSupport.stream(new DataFrameValueSpliterator<>(0, valueCount-1, valueCount, splitThreshold), isParallel());
        }
    }


    @Override()
    public double compute(Statistic1 statistic, int offset, int length) {
        Asserts.notNull(statistic, "The statistic object cannot be null");
        Asserts.check(offset >= 0, "The offset must be >= 0");
        Asserts.check(length >= 0, "The length must be >= 0");
        try {
            statistic.reset();
            for (int i=0; i<length; ++i) {
                var ordinal = offset + i;
                var value = getDoubleAt(ordinal);
                statistic.add(value);
            }
            return statistic.getValue();
        } catch (Exception ex) {
            throw new DataFrameException("Failed to compute statistic " + statistic.getType() + " for " + key(), ex);
        }
    }


    @Override()
    public final boolean equals(Object other) {
        if (getClass() == other.getClass() ) {
            final DataFrameVector otherVector = (DataFrameVector)other;
            if (size() != otherVector.size()) return false;
            else if (!key().equals(otherVector.key())) return false;
            else {
                for (int i=0; i<size(); ++i) {
                    final Object v1 = this.getValueAt(i);
                    final Object v2 = otherVector.getValueAt(i);
                    if (v1 == null && v2 != null) {
                        return false;
                    } else if (v1 != null && !v1.equals(v2)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }



    /**
     * A Spliterator implementation to iterate over all values in a DataFrame.
     * @param <A>   the row key type
     * @param <B>   the column key type
     */
    private class DataFrameValueSpliterator<A,B> implements Spliterator<DataFrameValue<A,B>> {

        private int start;
        private int end;
        private int position;
        private int length;
        private int splitThreshold;
        private DataFrameCursor<A,B> value;

        /**
         * Constructor
         * @param start             the start ordinal for this spliterator
         * @param end               the end ordinal for this spliterator
         * @param length            the length of the vector
         * @param splitThreshold    the split threshold
         */
        @SuppressWarnings("unchecked")
        private DataFrameValueSpliterator(int start, int end, int length, int splitThreshold) {
            Asserts.check(start <= end, "The from ordinal must be <= the to oridinal");
            Asserts.check(splitThreshold > 0, "The split threshold must be > 0");
            this.position = start;
            this.start = start;
            this.end = end;
            this.length = length;
            this.splitThreshold = splitThreshold;
            this.value = (DataFrameCursor<A,B>)frame.cursor();
            this.value = isRow() ? value.rowAt(ordinal()) : value.colAt(ordinal());
        }

        @Override
        public boolean tryAdvance(Consumer<? super DataFrameValue<A,B>> action) {
            Asserts.check(action != null, "The consumer action cannot be null");
            if (position <= end) {
                this.value = isRow() ? value.colAt(position) : value.rowAt(position);
                this.position++;
                action.accept(value);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Spliterator<DataFrameValue<A,B>> trySplit() {
            if (estimateSize() < splitThreshold) {
                return null;
            } else {
                var newStart = start;
                var halfSize = (end - start) / 2;
                var newEnd = newStart + halfSize;
                this.start = newEnd + 1;
                this.position = start;
                return new DataFrameValueSpliterator<>(newStart, newEnd, length, splitThreshold);
            }
        }

        @Override
        public long estimateSize() {
            return getExactSizeIfKnown();
        }

        @Override
        public int characteristics() {
            return SIZED | IMMUTABLE | SUBSIZED | CONCURRENT;
        }

        @Override
        public long getExactSizeIfKnown() {
            return (end - start) + 1;
        }
    }


}
