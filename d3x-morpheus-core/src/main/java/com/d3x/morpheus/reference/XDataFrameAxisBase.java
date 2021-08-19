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

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBuilder;
import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.frame.DataFrameAxis;
import com.d3x.morpheus.frame.DataFrameColumn;
import com.d3x.morpheus.frame.DataFrameException;
import com.d3x.morpheus.frame.DataFrameOptions;
import com.d3x.morpheus.frame.DataFrameRow;
import com.d3x.morpheus.frame.DataFrameVector;
import com.d3x.morpheus.index.Index;
import com.d3x.morpheus.range.Range;
import com.d3x.morpheus.util.Asserts;
import com.d3x.morpheus.util.Collect;
import com.d3x.morpheus.util.Parallel;
import com.d3x.morpheus.util.Tuple;

/**
 * A convenience base class for building DataFrameAxis implementations to expose bulk operations on the row and column dimension
 *
 * @param <X>   the key type for this axis
 * @param <Y>   the opposing dimension key type
 * @param <R>   the key type of the row dimension
 * @param <C>   the key type of the column dimension
 * @param <V>   the vector type for this dimension
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
abstract class XDataFrameAxisBase<X,Y,R,C,V extends DataFrameVector<?,?,R,C,?>,T extends DataFrameAxis<X,Y,R,C,V,T,G>,G> implements DataFrameAxis<X,Y,R,C,V,T,G> {

    private Type axisType;
    private boolean parallel;
    private Index<X> axis;
    private XDataFrame<R,C> frame;

    /**
     * Constructor
     * @param frame     the frame to operate on
     * @param parallel  true for parallel implementation
     * @param row       true if row dimension, false for column dimension
     */
    @SuppressWarnings("unchecked")
    XDataFrameAxisBase(XDataFrame<R,C> frame, boolean parallel, boolean row) {
        this.frame = frame;
        this.parallel = parallel;
        this.axisType = row ? Type.ROWS : Type.COLS;
        this.axis = row ? (Index<X>)frame.rowKeys() : (Index<X>)frame.colKeys();
    }


    /**
     * Returns a newly created vector for this axis
     * @param frame     the frame reference to pass to vector
     * @param ordinal   the ordinal for the vector
     * @return          the newly created vector
     */
    @SuppressWarnings("unchecked")
    private V createVector(XDataFrame<R,C> frame, int ordinal) {
        switch(axisType) {
            case ROWS:  return (V)new XDataFrameRow<>(frame, isParallel(), ordinal);
            case COLS:  return (V)new XDataFrameColumn<>(frame, isParallel(), ordinal);
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }

    /**
     * Returns a newly created filter over the frame based on the keys specified
     * @param frame the source frame to filter
     * @param keys  the keys for the new filter axis
     * @return      the newly created frame filter
     */
    @SuppressWarnings("unchecked")
    private DataFrame<R,C> createFilter(XDataFrame<R,C> frame, Iterable<X> keys) {
        if (axisType.isRow()) {
            var matches = Collect.asList(keys, this::contains);
            var newRowKeys = frame.rowKeys().filter((Iterable<R>)matches);
            var newColKeys = frame.colKeys().copy(true);
            return frame.filter(newRowKeys, newColKeys);
        } else {
            var matches = Collect.asList(keys, this::contains);
            var newRowKeys = frame.rowKeys().copy(true);
            var newColKeys = frame.colKeys().filter((Iterable<C>)matches);
            return frame.filter(newRowKeys, newColKeys);
        }
    }

    /**
     * Returns a reference to the frame to which this axis belongs
     * @return  the frame to which this axis belongs
     */
    protected final XDataFrame<R,C> frame() {
        return frame;
    }

    @Override
    public final int count() {
        return axis.size();
    }

    @Override
    public final X key(int ordinal) {
        return axis.getKey(ordinal);
    }

    @Override
    public final Stream<X> keys() {
        return axis.keys();
    }

    @Override
    public final Class<X> keyClass() {
        return axis.type();
    }

    @Override
    public final Array<X> keyArray() {
        return axis.toArray();
    }

    @Override
    public final IntStream ordinals() {
        return IntStream.range(0, axis.size());
    }

    @Override
    public boolean isEmpty() {
        return count() == 0;
    }

    @Override
    public boolean isParallel() {
        return parallel;
    }

    @Override
    public final Optional<X> firstKey() {
        return axis.first();
    }

    @Override
    public final Optional<X> lastKey() {
        return axis.last();
    }

    @Override
    public final Optional<X> lowerKey(X key) {
        return axis.previousKey(key);
    }

    @Override
    public final Optional<X> higherKey(X key) {
        return axis.nextKey(key);
    }

    @Override
    public final int ordinal(X key) {
        return axis.getOrdinal(key);
    }

    @Override
    public final int ordinalOrFail(X key) {
        var ordinal = axis.getOrdinal(key);
        if (ordinal >= 0) {
            return ordinal;
        } else {
            throw new DataFrameException("No match for key in axis: " + key);
        }
    }

    @Override
    public final boolean contains(X key) {
        return axis.contains(key);
    }

    @Override
    public final boolean containsAll(Iterable<X> keys) {
        return axis.containsAll(keys);
    }

    @Override
    public final Optional<V> first() {
        return count() > 0 ? Optional.of(createVector(frame, 0)) : Optional.empty();
    }

    @Override
    public final Optional<V> last() {
        return count() > 0 ? Optional.of(createVector(frame, count()-1)) : Optional.empty();
    }

    @Override
    public final Stream<V> stream() {
        if (count() == 0) {
            return Stream.empty();
        } else if (axisType == Type.ROWS) {
            var rowCount = frame.rowCount();
            var partitionSize = rowCount / Runtime.getRuntime().availableProcessors();
            var splitThreshold = Math.max(partitionSize, 10000);
            return StreamSupport.stream(new DataFrameVectorSpliterator<>(0, rowCount-1, rowCount, splitThreshold), frame.isParallel());
        } else if (axisType == Type.COLS) {
            var colCount = frame.colCount();
            var partitionSize = colCount / Runtime.getRuntime().availableProcessors();
            var splitThreshold = Math.max(partitionSize, 10000);
            return StreamSupport.stream(new DataFrameVectorSpliterator<>(0, colCount-1, colCount, splitThreshold), frame.isParallel());
        } else {
            throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }

    @Override
    public Stream<Class<?>> types() {
        switch (axisType) {
            case ROWS:  return frame.content().rowTypes();
            case COLS:  return frame.content().colTypes();
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Class<?> type(X key) {
        switch (axisType) {
            case ROWS:  return frame.content().rowType((R)key);
            case COLS:  return frame.content().colType((C)key);
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }


    @Override
    public final Iterator<V> iterator() {
        if (isEmpty()) {
            return Collections.emptyIterator();
        } else {
            var vector = createVector(frame, 0);
            return new Iterator<>() {
                private int ordinal;
                @Override
                public final boolean hasNext() {
                    return ordinal < count();
                }
                @Override
                @SuppressWarnings("unchecked")
                public final V next() {
                    if (vector instanceof XDataFrameRow) {
                        var row = (XDataFrameRow)vector;
                        return (V)row.atOrdinal(ordinal++);
                    } else {
                        var column = (XDataFrameColumn)vector;
                        return (V)column.atOrdinal(ordinal++);
                    }
                }
            };
        }
    }


    @Override
    @Parallel
    public final void forEach(Consumer<? super V> consumer) {
        if (parallel) {
            var count = count();
            var action = new ForEachVector(0, count - 1, consumer);
            ForkJoinPool.commonPool().invoke(action);
        } else if (count() > 0) {
            var count = count();
            var vector = createVector(frame, 0);
            if (vector instanceof XDataFrameRow) {
                var row = (XDataFrameRow)vector;
                for (int ordinal=0; ordinal < count; ++ordinal) {
                    row.atOrdinal(ordinal);
                    consumer.accept(vector);
                }
            } else {
                var column = (XDataFrameColumn)vector;
                for (int ordinal=0; ordinal < count; ++ordinal) {
                    column.atOrdinal(ordinal);
                    consumer.accept(vector);
                }
            }
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public final G groupBy(Y... keys) {
        switch (axisType) {
            case ROWS:  return (G)XDataFrameGroupingRows.of(frame, isParallel(), (Array<C>)Array.of(Stream.of(keys)));
            case COLS:  return (G)XDataFrameGroupingCols.of(frame, isParallel(), (Array<R>)Array.of(Stream.of(keys)));
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public final G groupBy(Function<V,Tuple> function) {
        switch (axisType) {
            case ROWS:  return (G)XDataFrameGroupingRows.of(frame, isParallel(), (Function<DataFrameRow<R,C>,Tuple>)function);
            case COLS:  return (G)XDataFrameGroupingCols.of(frame, isParallel(), (Function<DataFrameColumn<R,C>,Tuple>)function);
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public T filter(X... keys) {
        switch (axisType) {
            case ROWS:  return (T)select(keys).rows();
            case COLS:  return (T)select(keys).cols();
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final T filter(Iterable<X> keys) {
        switch (axisType) {
            case ROWS:  return (T)select(keys).rows();
            case COLS:  return (T)select(keys).cols();
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public final T filter(Predicate<V> predicate) {
        switch (axisType) {
            case ROWS:  return (T)select(predicate).rows();
            case COLS:  return (T)select(predicate).cols();
            default:    throw new DataFrameException("Unsupported axis type: " + axisType);
        }
    }


    @Override
    public final DataFrame<R,C> replaceKey(X key, X newKey) {
        if (axis.isFilter() && axisType == Type.ROWS) {
            throw new DataFrameException("Row axis is immutable for this frame, call copy() first");
        } else if (axis.isFilter() && axisType == Type.COLS) {
            throw new DataFrameException("Column axis is immutable for this frame, call copy() first");
        } else {
            this.axis.replace(key, newKey);
            return frame;
        }
    }


    @Override
    @SafeVarargs
    public final DataFrame<R,C> select(X... keys) {
        return createFilter(frame, Array.of(Stream.of(keys)));
    }


    @Override
    @Parallel
    public final DataFrame<R,C> select(Iterable<X> keys) {
        return createFilter(frame, keys);
    }


    @Override
    @Parallel
    public final DataFrame<R,C> select(Predicate<V> predicate) {
        if (isEmpty()) {
            return frame;
        } else if (parallel) {
            var count = count();
            var select = new Select(0, count-1, predicate);
            var keys = ForkJoinPool.commonPool().invoke(select);
            return createFilter(frame, keys);
        } else {
            var count = count();
            if (count == 0) {
                return createFilter(frame, Collections.emptyList());
            } else {
                var select = new Select(0, count-1, predicate);
                var keys = select.compute();
                return createFilter(frame, keys);
            }
        }
    }


    @Override
    public final DataFrame<R,C> select(int start, int length) {
        if (isEmpty()) {
            var rowType = frame.rows().keyClass();
            var colType = frame.cols().keyClass();
            return DataFrame.empty(rowType, colType);
        } else {
            var last = Math.min(start + length-1, count()-1);
            var keys = Range.of(start, last+1).map(this::key);
            return createFilter(frame, keys);
        }
    }


    @Override
    public final Optional<V> first(Predicate<V> predicate) {
        var count = count();
        var vector = createVector(frame, 0);
        if (vector instanceof XDataFrameRow) {
            var row = (XDataFrameRow)vector;
            for (int ordinal=0; ordinal < count; ++ordinal) {
                row.atOrdinal(ordinal);
                if (predicate.test(vector)) {
                    return Optional.of(vector);
                }
            }
        } else {
            var column = (XDataFrameColumn)vector;
            for (int ordinal=0; ordinal < count; ++ordinal) {
                column.atOrdinal(ordinal);
                if (predicate.test(vector)) {
                    return Optional.of(vector);
                }
            }
        }
        return Optional.empty();
    }


    @Override
    public final Optional<V> last(Predicate<V> predicate) {
        var count = count();
        var vector = createVector(frame, 0);
        if (vector instanceof XDataFrameRow) {
            var row = (XDataFrameRow)vector;
            for (int ordinal=count-1; ordinal >= 0; --ordinal) {
                row.atOrdinal(ordinal);
                if (predicate.test(vector)) {
                    return Optional.of(vector);
                }
            }
        } else {
            var column = (XDataFrameColumn)vector;
            for (int ordinal=count-1; ordinal >= 0; --ordinal) {
                column.atOrdinal(ordinal);
                if (predicate.test(vector)) {
                    return Optional.of(vector);
                }
            }
        }
        return Optional.empty();
    }


    @Override
    public final Optional<V> min(Comparator<V> comparator) {
        final MinVector task = new MinVector(0, count()-1, comparator);
        final V result = parallel ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        return Optional.ofNullable(result);
    }


    @Override
    public final Optional<V> max(Comparator<V> comparator) {
        final MaxVector task = new MaxVector(0, count()-1, comparator);
        final V result = parallel ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        return Optional.ofNullable(result);
    }


    /**
     * A RecursiveAction to iterate over vectors in a DataFrame, which could either be row or column vectors
     */
    private class ForEachVector extends RecursiveAction {

        private int from;
        private int to;
        private V vector;
        private Consumer<? super V> consumer;

        /**
         * Constructor
         * @param from      from ordinal
         * @param to        to ordinal
         * @param consumer  the vector consumer
         */
        @SuppressWarnings("unchecked")
        ForEachVector(int from, int to, Consumer<? super V> consumer) {
            this.from = from;
            this.to = to;
            this.consumer = consumer;
            this.vector = createVector(frame, 0);
            if (from > to) {
                throw new DataFrameException("The to index must be > from index");
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void compute() {
            try {
                var count = to - from + 1;
                var threshold = parallel ? DataFrameOptions.getRowSplitThreshold(frame) : Integer.MAX_VALUE;
                if (count <= threshold) {
                    if (vector instanceof XDataFrameRow) {
                        var row = (XDataFrameRow)vector;
                        for (int i=from; i<=to; ++i) {
                            row.atOrdinal(i);
                            this.consumer.accept(vector);
                        }
                    } else {
                        var column = (XDataFrameColumn)vector;
                        for (int i=from; i<=to; ++i) {
                            column.atOrdinal(i);
                            this.consumer.accept(vector);
                        }
                    }
                } else {
                    var splitCount = (to - from) / 2;
                    var midPoint = from + splitCount;
                    invokeAll(
                        new ForEachVector(from, midPoint, consumer),
                        new ForEachVector(midPoint+1, to, consumer)
                    );
                }
            } catch (Exception ex) {
                throw new DataFrameException("Failed to iterate over DataFrame axis vectors", ex);
            }
        }
    }


    /**
     * A RecursiveTask that implements a parallel select of keys that match a predicate while preserving order
     */
    private class Select extends RecursiveTask<Array<X>> {

        private int from;
        private int to;
        private int threshold;
        private Predicate<V> predicate;

        /**
         * Constructor
         * @param from      the from ordinal
         * @param to        the to row index in view space
         * @param predicate the predicate to match rows
         */
        Select(int from, int to, Predicate<V> predicate) {
            this.from = from;
            this.to = to;
            this.predicate = predicate;
            this.threshold = Integer.MAX_VALUE;
            if (isParallel()) {
                switch (axisType) {
                    case ROWS:  this.threshold = DataFrameOptions.getRowSplitThreshold(frame);      break;
                    case COLS:  this.threshold = DataFrameOptions.getColumnSplitThreshold(frame);   break;
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Array<X> compute() {
            var count = to - from + 1;
            var keyType = keyClass();
            if (count > threshold) {
                return split();
            } else {
                var rowCount = count();
                var vector = createVector(frame, 0);
                var builder = ArrayBuilder.of(rowCount > 0 ? rowCount : 10, keyType);
                if (vector instanceof XDataFrameRow) {
                    var row = (XDataFrameRow)vector;
                    for (int ordinal=from; ordinal<=to; ++ordinal) {
                        row.atOrdinal(ordinal);
                        if (predicate.test(vector)) {
                            builder.append((X)vector.key());
                        }
                    }
                } else {
                    var column = (XDataFrameColumn)vector;
                    for (int ordinal=from; ordinal<=to; ++ordinal) {
                        column.atOrdinal(ordinal);
                        if (predicate.test(vector)) {
                            builder.append((X)vector.key());
                        }
                    }
                }
                return builder.toArray();
            }
        }

        /**
         * Splits this task into two sub-tasks and executes them in parallel
         * @return  the join results from the two sub-tasks
         */
        private Array<X> split() {
            var splitCount = (to - from) / 2;
            var midPoint = from + splitCount;
            final Select left  = new Select(from, midPoint, predicate);
            final Select right = new Select(midPoint + 1, to, predicate);
            left.fork();
            final Array<X> rightAns = right.compute();
            final Array<X> leftAns  = left.join();
            var size = Math.max(rightAns.length() + leftAns.length(), 10);
            final ArrayBuilder<X> builder = ArrayBuilder.of(size, keyClass());
            builder.appendAll(leftAns);
            builder.appendAll(rightAns);
            return builder.toArray();
        }
    }


    /**
     * A Spliterator implementation to iterate over all vectors in this axis
     */
    private class DataFrameVectorSpliterator<A> implements Spliterator<A> {

        private A vector;
        private int position;
        private int start;
        private int end;
        private int count;
        private int splitThreshold;

        /**
         * Constructor
         * @param start             the start ordinal
         * @param end               the end ordinal
         * @param count             the row or column count if this represents a row or column axis
         * @param splitThreshold    the split threshold
         */
        @SuppressWarnings("unchecked")
        private DataFrameVectorSpliterator(int start, int end, int count, int splitThreshold) {
            Asserts.check(start <= end, "The from ordinal must be <= the to oridinal");
            Asserts.check(splitThreshold > 0, "The split threshold must be > 0");
            this.position = start;
            this.start = start;
            this.end = end;
            this.count = count;
            this.splitThreshold = splitThreshold;
            this.vector = (A)createVector(frame, start);
        }

        @Override
        public boolean tryAdvance(Consumer<? super A> action) {
            Asserts.check(action != null, "The consumer action cannot be null");
            if (position <= end) {
                if (vector instanceof XDataFrameRow) {
                    ((XDataFrameRow)vector).atOrdinal(position);
                    ++position;
                    action.accept(vector);
                    return true;
                } else if (vector instanceof XDataFrameColumn) {
                    ((XDataFrameColumn)vector).atOrdinal(position);
                    ++position;
                    action.accept(vector);
                    return true;
                } else {
                    throw new DataFrameException("Unsupported vector type: " + vector.getClass());
                }
            } else {
                return false;
            }
        }

        @Override
        public Spliterator<A> trySplit() {
            if (estimateSize() < splitThreshold) {
                return null;
            } else {
                var newStart = start;
                var halfSize = (end - start) / 2;
                var newEnd = newStart + halfSize;
                this.start = newEnd + 1;
                this.position = start;
                return new DataFrameVectorSpliterator<>(newStart, newEnd, count, splitThreshold);
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


    /**
     * A RecursiveTask implementation that determines the min() of rows or columns
     */
    private class MinVector extends RecursiveTask<V> {

        private int fromOrdinal;
        private int toOrdinal;
        private int threshold;
        private Comparator<V> comparator;

        /**
         * Constructor
         * @param fromOrdinal       the from ordinal
         * @param toOrdinal         the to ordinal
         * @param comparator        the comparator that defines order
         */
        MinVector(int fromOrdinal, int toOrdinal, Comparator<V> comparator) {
            Asserts.check(fromOrdinal >= 0, "from ordinal must be > 0");
            Asserts.check(toOrdinal >= 0, "to ordinal must be > 0");
            Asserts.check(toOrdinal > fromOrdinal, "The toOrdinal must be > fromOrdinal");
            this.fromOrdinal = fromOrdinal;
            this.toOrdinal = toOrdinal;
            this.comparator = comparator;
            this.threshold = Integer.MAX_VALUE;
            if (parallel) {
                this.threshold = Math.max(1000, count() / Runtime.getRuntime().availableProcessors());
            }
        }

        @Override
        protected V compute() {
            if (count() == 0) return null;
            var count = toOrdinal - fromOrdinal + 1;
            if (count > threshold) {
                var partitionSize = (toOrdinal - fromOrdinal) / 2;
                var midPoint = fromOrdinal + partitionSize;
                var left  = new MinVector(fromOrdinal, midPoint, comparator);
                var right = new MinVector(midPoint + 1, toOrdinal, comparator);
                left.fork();
                var rightAns = right.compute();
                var leftAns  = left.join();
                var compare = comparator.compare(leftAns, rightAns);
                return compare > 0 ? rightAns : leftAns;
            } else {
                return argMin();
            }
        }

        /**
         * Returns the min row or column vector
         * @return  the min row or column vector
         */
        @SuppressWarnings("unchecked")
        private V argMin() {
            var minVector = createVector(frame, fromOrdinal);
            var otherVector = createVector(frame, fromOrdinal);
            if (minVector instanceof XDataFrameRow) {
                var minRow = (XDataFrameRow<R,C>)minVector;
                var otherRow = (XDataFrameRow<R,C>)otherVector;
                for (int i=fromOrdinal+1; i<=toOrdinal; ++i) {
                    var compare = comparator.compare(minVector, (V)otherRow.atOrdinal(i));
                    if (compare > 0) minRow.atOrdinal(i);
                }
            } else {
                var minColumn = (XDataFrameColumn<R,C>)minVector;
                var otherColumn = (XDataFrameColumn<R,C>)otherVector;
                for (int i=fromOrdinal+1; i<=toOrdinal; ++i) {
                    var compare = comparator.compare(minVector, (V)otherColumn.atOrdinal(i));
                    if (compare > 0) minColumn.atOrdinal(i);
                }
            }
            return minVector;
        }
    }



    /**
     * A RecursiveTask implementation that determines the max() of rows or columns
     */
    private class MaxVector extends RecursiveTask<V> {

        private int fromOrdinal;
        private int toOrdinal;
        private int threshold;
        private Comparator<V> comparator;

        /**
         * Constructor
         * @param fromOrdinal       the from ordinal
         * @param toOrdinal         the to ordinal
         * @param comparator        the comparator that defines order
         */
        MaxVector(int fromOrdinal, int toOrdinal, Comparator<V> comparator) {
            this.fromOrdinal = fromOrdinal;
            this.toOrdinal = toOrdinal;
            this.comparator = comparator;
            this.threshold = Integer.MAX_VALUE;
            if (parallel) {
                this.threshold = Math.max(1000, count() / Runtime.getRuntime().availableProcessors());
            }
        }

        @Override
        protected V compute() {
            if (count() == 0) return null;
            var count = toOrdinal - fromOrdinal + 1;
            if (count > threshold) {
                var partitionSize = (toOrdinal - fromOrdinal) / 2;
                var midPoint = fromOrdinal + partitionSize;
                var left  = new MaxVector(fromOrdinal, midPoint, comparator);
                var right = new MaxVector(midPoint + 1, toOrdinal, comparator);
                left.fork();
                var rightAns = right.compute();
                var leftAns  = left.join();
                var compare = comparator.compare(leftAns, rightAns);
                return compare < 0 ? rightAns : leftAns;
            } else {
                return argMax();
            }
        }

        /**
         * Returns the min row or column vector
         * @return  the min row or column vector
         */
        @SuppressWarnings("unchecked")
        private V argMax() {
            var maxVector = createVector(frame, fromOrdinal);
            var otherVector = createVector(frame, fromOrdinal);
            if (maxVector instanceof XDataFrameRow) {
                var maxRow = (XDataFrameRow<R,C>)maxVector;
                var otherRow = (XDataFrameRow<R,C>)otherVector;
                for (int i=fromOrdinal+1; i<=toOrdinal; ++i) {
                    var compare = comparator.compare((V)otherRow.atOrdinal(i), (V)maxRow);
                    if (compare > 0) maxRow.atOrdinal(i);
                }
            } else {
                var maxColumn = (XDataFrameColumn<R,C>)maxVector;
                var otherColumn = (XDataFrameColumn<R,C>)otherVector;
                for (int i=fromOrdinal+1; i<=toOrdinal; ++i) {
                    var compare = comparator.compare((V)otherColumn.atOrdinal(i), (V)maxColumn);
                    if (compare > 0) maxColumn.atOrdinal(i);
                }
            }
            return maxVector;
        }
    }


}
