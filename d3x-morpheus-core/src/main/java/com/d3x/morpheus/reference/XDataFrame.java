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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBuilder;
import com.d3x.morpheus.array.ArrayType;
import com.d3x.morpheus.array.ArrayUtils;
import com.d3x.morpheus.frame.*;
import com.d3x.morpheus.index.IndexMapper;
import com.d3x.morpheus.reference.algebra.XDataFrameAlgebra;
import com.d3x.morpheus.reference.regress.XDataFrameRegression;
import com.d3x.morpheus.index.Index;
import com.d3x.morpheus.stats.Stats;
import com.d3x.morpheus.util.Asserts;
import com.d3x.morpheus.util.Bounds;
import com.d3x.morpheus.util.functions.ToBooleanFunction;
import com.d3x.morpheus.util.text.Formats;

/**
 * The reference implementation of the DataFrame interface.
 *
 * @param <C>   the column key type
 * @param <R>   the row key type
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
class XDataFrame<R,C> implements DataFrame<R,C>, Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    private boolean parallel;
    private XDataFrameEvents events;
    private XDataFrameRows<R,C> rows;
    private XDataFrameColumns<R,C> cols;
    private XDataFrameContent<R,C> data;


    /**
     * Constructor
     * @param rowKeys   the row keys for DataFrame
     * @param colKeys   the column keys for DataFrame
     * @param type      the data type for columns
     * @param parallel  true for parallel implementation
     */
    XDataFrame(Index<R> rowKeys, Index<C> colKeys, Class<?> type, boolean parallel) {
        this(new XDataFrameContent<>(rowKeys, colKeys, type), parallel);
    }

    /**
     * Private constructor used to create DataFrame filtered views
     * @param data      the data content for this DataFrame
     * @param parallel  true for parallel implementation
     */
    XDataFrame(XDataFrameContent<R,C> data, boolean parallel) {
        this.data = data;
        this.parallel = parallel;
        this.events = new XDataFrameEvents();
        this.rows = new XDataFrameRows<>(this, parallel);
        this.cols = new XDataFrameColumns<>(this, parallel);
    }


    /**
     * Configures columns based on the consumer
     * @param consumer  the column consumer
     * @return          this frame
     */
    final XDataFrame<R,C> configure(Consumer<DataFrameColumns<R,C>> consumer) {
        consumer.accept(cols());
        return this;
    }


    /**
     * Returns a shallow copy of the frame replacing the row keys
     * @param mapper    the mapper to map row keys
     * @param <X>       the new row key type
     * @return          the shallow copy of the frame
     */
    final <X> XDataFrame<X,C> mapRowKeys(IndexMapper<R,X> mapper) {
        return new XDataFrame<>(data.mapRowKeys(mapper), isParallel());
    }


    /**
     * Returns a shallow copy of the frame replacing the column keys
     * @param mapper    the mapper to map column keys
     * @param <Y>       the new row key type
     * @return          the shallow copy of the frame
     */
    final <Y> XDataFrame<R,Y> mapColKeys(IndexMapper<C,Y> mapper) {
        return new XDataFrame<>(data.mapColKeys(mapper), isParallel());
    }


    /**
     * Returns a shallow copy of this frame replacing the row key index
     * @param rowKeys   the row key index replacement
     * @param <X>       the row key type
     * @return          the shallow copy of the frame
     */
    final <X> XDataFrame<X,C> withRowKeys(Index<X> rowKeys) {
        return new XDataFrame<>(data.withRowKeys(rowKeys), isParallel());
    }


    /**
     * Returns a shallow copy of this frame replacing the row key index
     * @param colKeys   the column key index replacement
     * @param <Y>       the column key type
     * @return          the shallow copy of the frame
     */
    final <Y> XDataFrame<R,Y> withColKeys(Index<Y> colKeys) {
        return new XDataFrame<>(data.withColKeys(colKeys), isParallel());
    }


    /**
     * Returns a filter of this frame based on the row and column dimensions provided
     * @param rowKeys   the row keys for frame, which could include a subset of row keys
     * @param colKeys   the column keys for frame, which could include a subset of column keys
     */
    final XDataFrame<R,C> filter(Index<R> rowKeys, Index<C> colKeys) {
        return new XDataFrame<>(data.filter(rowKeys, colKeys), parallel);
    }


    /**
     * Returns the algebraic interface for this DataFrame
     * @return      the algebraic interface
     */
    private DataFrameAlgebra<R,C> algebra() {
        return XDataFrameAlgebra.create(this);
    }


    /**
     * Returns a reference to the index of row keys
     * @return  the row keys
     */
    final Index<R> rowKeys() {
        return data.rowKeys();
    }


    /**
     * Returns a reference to the index of column keys
     * @return  the column keys
     */
    final Index<C> colKeys() {
        return data.colKeys();
    }


    /**
     * Returns direct access to the contents for this frame
     * @return  the contents for this frame
     */
    final XDataFrameContent<R,C> content() {
        return data;
    }


    @Override()
    public final DataFrame<R,C> parallel() {
        return parallel ? this : new XDataFrame<>(data, true);
    }


    @Override()
    public final DataFrame<R,C> sequential() {
        return parallel ? new XDataFrame<>(data, false) : this;
    }


    @Override()
    public final int rowCount() {
        return rowKeys().size();
    }


    @Override()
    public final int colCount() {
        return colKeys().size();
    }

    @Override
    public boolean isEmpty() {
        return rowCount() == 0 || colCount() == 0;
    }

    @Override()
    public final boolean isParallel() {
        return parallel;
    }


    @Override()
    public final DataFrameRows<R,C> rows() {
        return rows;
    }


    @Override()
    public final DataFrameColumns<R,C> cols() {
        return cols;
    }


    @Override()
    public final DataFrameCursor<R,C> cursor() {
        return data.cursor(this);
    }


    @Override
    public final DataFrameValue<R,C> get(R rowKey, C colKey) {
        return data.cursor(this).atKeys(rowKey, colKey);
    }


    @Override
    public final DataFrameValue<R, C> at(int rowOrdinal, int colOrdinal) {
        return data.cursor(this).atOrdinals(rowOrdinal, colOrdinal);
    }


    @Override
    public final DataFrameRow<R,C> row(R rowKey) {
        var ordinal = rowKeys().getOrdinal(rowKey);
        if (ordinal >= 0) {
            return new XDataFrameRow<>(this, parallel, ordinal);
        } else {
            throw new DataFrameException("No match for row key: " + rowKey);
        }
    }


    @Override
    public final DataFrameRow<R,C> rowAt(int rowOrdinal) {
        if (rowOrdinal < 0 || rowOrdinal >= rowCount()) {
            throw new DataFrameException("Row ordinal out of bounds");
        } else {
            return new XDataFrameRow<>(this, parallel, rowOrdinal);
        }
    }


    @Override
    public final DataFrameColumn<R,C> col(C colKey) {
        var ordinal = colKeys().getOrdinal(colKey);
        if (ordinal >= 0) {
            return new XDataFrameColumn<>(this, parallel, ordinal);
        } else {
            throw new DataFrameException("No match for column key: " + colKey);
        }
    }


    @Override
    public final DataFrameColumn<R,C> colAt(int colOrdinal) {
        if (colOrdinal < 0 || colOrdinal >= colCount()) {
            throw new DataFrameException("Column ordinal out of bounds");
        } else {
            return new XDataFrameColumn<>(this, parallel, colOrdinal);
        }
    }


    @Override
    public int count(Predicate<DataFrameValue<R,C>> predicate) {
        var count = new AtomicInteger();
        this.forEachValue(v -> {
            if (predicate.test(v)) {
                count.incrementAndGet();
            }
        });
        return count.get();
    }


    @Override()
    public Optional<DataFrameValue<R,C>> min(Predicate<DataFrameValue<R,C>> predicate) {
        if (rowCount() == 0 || colCount() == 0) {
            return Optional.empty();
        } else if (rowCount() > colCount()) {
            var task = new MinMaxValueTask(0, rowCount(), true, predicate);
            return isParallel() ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        } else {
            var task = new MinMaxValueTask(0, colCount(), true, predicate);
            return isParallel() ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        }
    }


    @Override()
    public Optional<DataFrameValue<R,C>> max(Predicate<DataFrameValue<R,C>> predicate) {
        if (rowCount() == 0 || colCount() == 0) {
            return Optional.empty();
        } else if (rowCount() > colCount()) {
            var task = new MinMaxValueTask(0, rowCount(), false, predicate);
            return  isParallel() ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        } else {
            var task = new MinMaxValueTask(0, colCount(), false, predicate);
            return isParallel() ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        }
    }


    @Override
    public <V> Optional<Bounds<V>> bounds(Predicate<DataFrameValue<R, C>> predicate) {
        if (rowCount() == 0 || colCount() == 0) {
            return Optional.empty();
        } else if (rowCount() > colCount()) {
            var task = new BoundsTask<V>(0, rowCount(), predicate);
            return isParallel() ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        } else {
            var task = new BoundsTask<V>(0, colCount(), predicate);
            return isParallel() ? ForkJoinPool.commonPool().invoke(task) : task.compute();
        }
    }


    @Override()
    public final DataFrame<R,C> forEachValue(Consumer<DataFrameValue<R,C>> consumer) {
        if (parallel && colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = (rowCount() * colCount()) / Runtime.getRuntime().availableProcessors();
            var action = new ForEachValue(0, toIndex, threshold, consumer);
            ForkJoinPool.commonPool().invoke(action);
        } else if (colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = Integer.MAX_VALUE;
            var action = new ForEachValue(0, toIndex, threshold, consumer);
            action.compute();
        }
        return this;
    }


    @Override()
    public final DataFrame<R,C> applyBooleans(ToBooleanFunction<DataFrameValue<R,C>> mapper) {
        if (parallel && colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = (rowCount() * colCount()) / Runtime.getRuntime().availableProcessors();
            var action = new ApplyBooleans(0, toIndex, threshold, mapper);
            ForkJoinPool.commonPool().invoke(action);
        } else if (colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = Integer.MAX_VALUE;
            var action = new ApplyBooleans(0, toIndex, threshold, mapper);
            action.compute();
        }
        return this;
    }


    @Override()
    public final DataFrame<R,C> applyInts(ToIntFunction<DataFrameValue<R,C>> mapper) {
        if (parallel && colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = (rowCount() * colCount()) / Runtime.getRuntime().availableProcessors();
            var action = new ApplyInts(0, toIndex, threshold, mapper);
            ForkJoinPool.commonPool().invoke(action);
        } else if (colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = Integer.MAX_VALUE;
            var action = new ApplyInts(0, toIndex, threshold, mapper);
            action.compute();
        }
        return this;
    }


    @Override()
    public final DataFrame<R,C> applyLongs(ToLongFunction<DataFrameValue<R,C>> mapper) {
        if (parallel && colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = (rowCount() * colCount()) / Runtime.getRuntime().availableProcessors();
            var action = new ApplyLongs(0, toIndex, threshold, mapper);
            ForkJoinPool.commonPool().invoke(action);
        } else if (colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = Integer.MAX_VALUE;
            var action = new ApplyLongs(0, toIndex, threshold, mapper);
            action.compute();
        }
        return this;
    }


    @Override()
    public final DataFrame<R,C> applyDoubles(ToDoubleFunction<DataFrameValue<R,C>> mapper) {
        if (parallel && colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = (rowCount() * colCount()) / Runtime.getRuntime().availableProcessors();
            var action = new ApplyDoubles(0, toIndex, threshold, mapper);
            ForkJoinPool.commonPool().invoke(action);
        } else if (colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = Integer.MAX_VALUE;
            var action = new ApplyDoubles(0, toIndex, threshold, mapper);
            action.compute();
        }
        return this;
    }


    @Override()
    public final DataFrame<R,C> applyValues(Function<DataFrameValue<R,C>,?> mapper) {
        if (parallel && colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = (rowCount() * colCount()) / Runtime.getRuntime().availableProcessors();
            var action = new ApplyValues(0, toIndex, threshold, mapper);
            ForkJoinPool.commonPool().invoke(action);
        } else if (colCount() > 0) {
            var toIndex = rowCount() * colCount() - 1;
            var threshold = Integer.MAX_VALUE;
            var action = new ApplyValues(0, toIndex, threshold, mapper);
            action.compute();
        }
        return this;
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final DataFrame<R,C> copy() {
        try {
            var newRowKeys = rowKeys().toArray();
            var newColKeys = Index.of(colKeys().type(),  colKeys().size());
            var content = new XDataFrameContent<>(newRowKeys, newColKeys, Object.class);
            var newFrame = new XDataFrame<R,C>(content, parallel);
            var newColumn = newFrame.cols().cursor();
            this.cols().sequential().forEach(column -> {
                var colKey = column.key();
                var colClass = column.dataClass();
                var arrayType = ArrayType.of(colClass);
                newFrame.cols().add(colKey, colClass);
                newColumn.atKey(colKey);
                switch (arrayType) {
                    case BOOLEAN:       newColumn.applyBooleans(v -> column.getBooleanAt(v.rowOrdinal()));  break;
                    case INTEGER:       newColumn.applyInts(v -> column.getIntAt(v.rowOrdinal()));          break;
                    case LONG:          newColumn.applyLongs(v -> column.getLongAt(v.rowOrdinal()));        break;
                    case DOUBLE:        newColumn.applyDoubles(v -> column.getDoubleAt(v.rowOrdinal()));    break;
                    case LOCAL_TIME:    newColumn.applyLongs(v -> column.getLongAt(v.rowOrdinal()));        break;
                    case LOCAL_DATE:    newColumn.applyLongs(v -> column.getLongAt(v.rowOrdinal()));        break;
                    case ENUM:          newColumn.applyInts(v -> column.getIntAt(v.rowOrdinal()));          break;
                    default:            newColumn.applyValues(v -> column.getValueAt(v.rowOrdinal()));      break;
                }
            });
            return newFrame;
        } catch (Throwable t) {
            throw new DataFrameException("Failed to create a deep copy of DataFrame", t);
        }
    }


    @Override()
    public final DataFrame<R,C> update(DataFrame<R,C> update, boolean addRows, boolean addColumns) throws DataFrameException {
        try {
            var other = (XDataFrame<R,C>)update;
            if (addRows) rows().addAll(update.rows().keyArray());
            if (addColumns) cols().addAll(update);
            var rowKeys = rowKeys().intersect(other.rowKeys());
            var colKeys = colKeys().intersect(other.colKeys());
            var sourceRows = other.rowKeys().ordinals(rowKeys).toArray();
            var sourceCols = other.colKeys().ordinals(colKeys).toArray();
            var targetRows = this.rowKeys().ordinals(rowKeys).toArray();
            var targetCols = this.colKeys().ordinals(colKeys).toArray();
            var sourceCursor = other.cursor();
            var targetCursor = this.cursor();
            for (int i=0; i<sourceRows.length; ++i) {
                sourceCursor.rowAt(sourceRows[i]);
                targetCursor.rowAt(targetRows[i]);
                for (int j=0; j<sourceCols.length; ++j) {
                    sourceCursor.colAt(sourceCols[j]);
                    targetCursor.colAt(targetCols[j]);
                    final Object value = sourceCursor.getValue();
                    targetCursor.setValue(value);
                }
            }
            return this;
        } catch (Throwable t) {
            throw new DataFrameException("DataFrame data bulk update failed: " + t.getMessage(), t);
        }
    }


    @Override()
    public DataFrame<R,C> sign() throws DataFrameException {
        var rowCount = rowCount();
        var colCount = colCount();
        var rowIndex = Index.of(rows().keyArray());
        var colIndex = Index.of(cols().keyArray());
        var result = (XDataFrame<R,C>)DataFrame.ofInts(rowIndex, colIndex);
        var cursor1 = this.cursor();
        var cursor2 = result.cursor();
        for (int i=0; i<rowCount; ++i) {
            cursor1.rowAt(i);
            cursor2.rowAt(i);
            for (int j=0; j<colCount; ++j) {
                cursor1.colAt(j);
                cursor2.colAt(j);
                var value = cursor1.getDouble();
                cursor2.setInt(0);
                if (value > 0d) {
                    cursor2.setInt(1);
                } else if (value < 0d) {
                    cursor2.setInt(-1);
                }
            }
        }
        return result;
    }


    @Override()
    public DataFrameOutput<R,C> out() {
        return new XDataFrameOutput<>(this, new Formats());
    }


    @Override()
    public DataFrameFill fill() {
        return new XDataFrameFill<>(this);
    }


    @Override()
    public final Stats<Double> stats() {
        return new XDataFrameStats<>(true, this);
    }


    @Override
    public DataFrame<C, R> transpose() {
        return new XDataFrame<>(data.transpose(), isParallel());
    }


    @Override
    public Decomposition decomp() {
        return algebra().decomp();
    }


    @Override
    public DataFrame<Integer,Integer> inverse() throws DataFrameException {
        return algebra().inverse();
    }


    @Override
    public DataFrame<Integer,Integer> solve(DataFrame<?,?> rhs) throws DataFrameException {
        return algebra().solve(rhs);
    }


    @Override
    public DataFrame<R,C> plus(Number scalar) throws DataFrameException {
        return algebra().plus(scalar);
    }


    @Override
    public DataFrame<R,C> plus(DataFrame<?, ?> other) throws DataFrameException {
        return algebra().plus(other);
    }


    @Override
    public DataFrame<R,C> minus(Number scalar) throws DataFrameException {
        return algebra().minus(scalar);
    }


    @Override
    public DataFrame<R,C> minus(DataFrame<?,?> other) throws DataFrameException {
        return algebra().minus(other);
    }


    @Override
    public DataFrame<R,C> times(Number scalar) throws DataFrameException {
        return algebra().times(scalar);
    }


    @Override
    public DataFrame<R,C> times(DataFrame<?,?> other) throws DataFrameException {
        return algebra().times(other);
    }


    @Override
    public <X,Y> DataFrame<R,Y> dot(DataFrame<X,Y> right) throws DataFrameException {
        return algebra().dot(right);
    }


    @Override
    public DataFrame<R,C> divide(Number scalar) throws DataFrameException {
        return algebra().divide(scalar);
    }


    @Override
    public DataFrame<R,C> divide(DataFrame<?,?> other) throws DataFrameException {
        return algebra().divide(other);
    }


    @Override()
    public final DataFrameRank<R,C> rank() {
        return new XDataFrameRank<>(this);
    }


    @Override()
    public DataFrameEvents events() {
        return events;
    }


    @Override()
    public DataFrameWrite<R,C> write() {
        return new XDataFrameWrite<>(this);
    }


    @Override()
    public DataFrameExport export() {
        return new XDataFrameExport<>(this);
    }


    @Override
    public DataFrameCap<R,C> cap(boolean inPlace) {
        return new XDataFrameCap<>(inPlace, this);
    }


    @Override()
    public DataFrameCalculate<R,C> calc() {
        return new XDataFrameCalculate<>(this);
    }


    @Override()
    public DataFramePCA<R,C> pca() {
        return new XDataFramePCA<>(this);
    }


    @Override
    public DataFrameSmooth<R, C> smooth(boolean inPlace) {
        return new XDataFrameSmooth<>(inPlace, this);
    }


    @Override
    public DataFrameRegression<R, C> regress() {
        return new XDataFrameRegression<>(this);
    }


    @Override()
    public final DataFrame<R,C> addAll(DataFrame<R,C> other) {
        try {
            rows().addAll(other);
            cols().addAll(other);
            return this;
        } catch (Throwable t) {
            throw new DataFrameException("Failed to add rows/columns from other DataFrame: " + t.getMessage(), t);
        }
    }


    @Override()
    public Iterator<DataFrameValue<R,C>> iterator() {
        var value = cursor();
        return new Iterator<>() {
            private int rowIndex = 0;
            private int colIndex = 0;
            @Override
            public DataFrameValue<R,C> next() {
                value.atOrdinals(rowIndex++, colIndex);
                if (rowIndex == rowCount()) {
                    rowIndex = 0;
                    colIndex++;
                }
                return value;
            }
            @Override
            public boolean hasNext() {
                return rowIndex < rowCount() && colIndex < colCount();
            }
        };
    }


    @Override()
    public Iterator<DataFrameValue<R,C>> iterator(Predicate<DataFrameValue<R,C>> predicate) {
        var value = cursor();
        return new Iterator<>() {
            private int rowIndex = 0;
            private int colIndex = 0;
            @Override
            public DataFrameValue<R,C> next() {
                value.atOrdinals(rowIndex++, colIndex);
                if (rowIndex == rowCount()) {
                    rowIndex = 0;
                    colIndex++;
                }
                return value;
            }
            @Override
            public boolean hasNext() {
                while (rowIndex < rowCount() && colIndex < colCount()) {
                    value.atOrdinals(rowIndex, colIndex);
                    if (predicate == null || predicate.test(value)) {
                        return true;
                    } else {
                        ++rowIndex;
                        if (rowIndex == rowCount()) {
                            rowIndex = 0;
                            colIndex++;
                        }
                    }
                }
                return false;
            }
        };
    }


    @Override()
    public final DataFrame<R, C> head(int count) {
        var indexes = IntStream.range(0, Math.min(count, rowCount()));
        var keys = indexes.mapToObj(i -> rows().key(i)).collect(ArrayUtils.toArray());
        var newRowAxis = rowKeys().filter(keys);
        var newColAxis = colKeys();
        var newContents = data.filter(newRowAxis, newColAxis);
        return new XDataFrame<>(newContents, parallel);
    }


    @Override()
    public final DataFrame<R, C> tail(int count) {
        var indexes = IntStream.range(Math.max(0, rowCount() - count), rowCount());
        var keys = indexes.mapToObj(i -> rows().key(i)).collect(ArrayUtils.toArray());
        var newRowAxis = rowKeys().filter(keys);
        var newColAxis = colKeys();
        var newContents = data.filter(newRowAxis, newColAxis);
        return new XDataFrame<>(newContents, parallel);
    }


    @Override()
    public final DataFrame<R,C> left(int count) {
        var colKeys = colKeys().toArray(0, Math.min(colCount(), count));
        var newColAxis = colKeys().filter(colKeys);
        var newContents = data.filter(rowKeys(), newColAxis);
        return new XDataFrame<>(newContents, parallel);
    }


    @Override()
    public final DataFrame<R,C> right(int count) {
        var colKeys = colKeys().toArray(Math.max(0, colCount() - count), colCount());
        var newColAxis = colKeys().filter(colKeys);
        var newContents = data.filter(rowKeys(), newColAxis);
        return new XDataFrame<>(newContents, parallel);
    }


    @Override()
    public final DataFrame<R,C> select(Iterable<R> rowKeys, Iterable<C> colKeys) {
        var newRowAxis = rowKeys().filter(rowKeys);
        var newColAxis = colKeys().filter(colKeys);
        var newContents = data.filter(newRowAxis, newColAxis);
        return new XDataFrame<>(newContents, parallel);
    }


    @Override()
    public final DataFrame<R,C> select(Predicate<DataFrameRow<R,C>> rowPredicate, Predicate<DataFrameColumn<R,C>> colPredicate) {
        var selectRows = new SelectRows(0, rowCount()-1, rowPredicate);
        var selectCols = new SelectColumns(0, colCount()-1, colPredicate);
        var rowKeys = isParallel() ? ForkJoinPool.commonPool().invoke(selectRows) : selectRows.compute();
        var colKeys = isParallel() ? ForkJoinPool.commonPool().invoke(selectCols) : selectCols.compute();
        var newRowAxis = rowKeys().filter(rowKeys);
        var newColAxis = colKeys().filter(colKeys);
        var newContents = data.filter(newRowAxis, newColAxis);
        return new XDataFrame<>(newContents, parallel);
    }


    @Override
    public DataFrame<R, C> mapToBooleans(ToBooleanFunction<DataFrameValue<R, C>> mapper) {
        var rowKeys = rows().keyArray();
        var colKeys = cols().keyArray();
        var result = DataFrame.ofBooleans(rowKeys, colKeys);
        result.cols().forEach(writeColumn -> {
            var colKey = writeColumn.key();
            this.cols().forEachValue(colKey, v -> {
                var value = mapper.applyAsBoolean(v);
                writeColumn.setBooleanAt(v.rowOrdinal(), value);
            });
        });
        return result;
    }


    @Override
    public DataFrame<R, C> mapToInts(ToIntFunction<DataFrameValue<R, C>> mapper) {
        var rowKeys = rows().keyArray();
        var colKeys = cols().keyArray();
        var result = DataFrame.ofInts(rowKeys, colKeys);
        result.cols().forEach(writeColumn -> {
            var colKey = writeColumn.key();
            this.cols().forEachValue(colKey, v -> {
                var value = mapper.applyAsInt(v);
                writeColumn.setIntAt(v.rowOrdinal(), value);
            });
        });
        return result;
    }


    @Override
    public DataFrame<R, C> mapToLongs(ToLongFunction<DataFrameValue<R, C>> mapper) {
        var rowKeys = rows().keyArray();
        var colKeys = cols().keyArray();
        var result = DataFrame.ofLongs(rowKeys, colKeys);
        result.cols().forEach(writeColumn -> {
            var colKey = writeColumn.key();
            this.cols().forEachValue(colKey, v -> {
                var value = mapper.applyAsLong(v);
                writeColumn.setLongAt(v.rowOrdinal(), value);
            });
        });
        return result;
    }


    @Override()
    public final DataFrame<R,C> mapToDoubles(ToDoubleFunction<DataFrameValue<R,C>> mapper) {
        var rowKeys = rows().keyArray();
        var colKeys = cols().keyArray();
        var result = DataFrame.ofDoubles(rowKeys, colKeys);
        result.cols().forEach(writeColumn -> {
            var colKey = writeColumn.key();
            this.cols().forEachValue(colKey, v -> {
                var value = mapper.applyAsDouble(v);
                writeColumn.setDoubleAt(v.rowOrdinal(), value);
            });
        });
        return result;
    }


    @Override
    public <T> DataFrame<R,C> mapToObjects(Class<T> type, Function<DataFrameValue<R,C>,T> mapper) {
        var rowKeys = rows().keyArray();
        var colKeys = cols().keyArray();
        var content = new XDataFrameContent<R,C>(rowKeys, colKeys, type);
        var result = new XDataFrame<R,C>(content, parallel);
        result.cols().forEach(writeColumn -> {
            var colKey = writeColumn.key();
            this.cols().forEachValue(colKey, v -> {
                var value = mapper.apply(v);
                writeColumn.setValueAt(v.rowOrdinal(), value);
            });
        });
        return result;
    }


    @Override
    public DataFrame<R,C> mapToBooleans(C colKey, ToBooleanFunction<DataFrameValue<R,C>> mapper) {
        return new XDataFrame<>(content().mapToBooleans(this, colKey, mapper), isParallel());
    }


    @Override
    public DataFrame<R,C> mapToInts(C colKey, ToIntFunction<DataFrameValue<R,C>> mapper) {
        return new XDataFrame<>(content().mapToInts(this, colKey, mapper), isParallel());
    }


    @Override
    public DataFrame<R,C> mapToLongs(C colKey, ToLongFunction<DataFrameValue<R,C>> mapper) {
        return new XDataFrame<>(content().mapToLongs(this, colKey, mapper), isParallel());
    }


    @Override
    public DataFrame<R,C> mapToDoubles(C colKey, ToDoubleFunction<DataFrameValue<R,C>> mapper) {
        return new XDataFrame<>(content().mapToDoubles(this, colKey, mapper), isParallel());
    }


    @Override
    public <T> DataFrame<R,C> mapToObjects(C colKey, Class<T> type, Function<DataFrameValue<R,C>,T> mapper) {
        return new XDataFrame<>(content().mapToObjects(this, colKey, type, mapper), isParallel());
    }


    @Override
    public final boolean getBoolean(R rowKey, C colKey) {
        var row = data.rowCoordinate(rowKey);
        var col = data.colCoordinate(colKey);
        return row >= 0 && col >= 0 && data.booleanAt(row, col);
    }

    @Override
    public final boolean getBooleanAt(int rowOrdinal, int colOrdinal) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.booleanAt(row, col);
    }

    @Override
    public final int getInt(R rowKey, C colKey) {
        var row = data.rowCoordinate(rowKey);
        var col = data.colCoordinate(colKey);
        return row < 0 || col < 0 ? 0 : data.intAt(row, col);
    }

    @Override
    public final int getIntAt(int rowOrdinal, int colOrdinal) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.intAt(row, col);
    }

    @Override
    public final long getLong(R rowKey, C colKey) {
        var row = data.rowCoordinate(rowKey);
        var col = data.colCoordinate(colKey);
        return row < 0 || col < 0 ? 0L : data.longAt(row, col);
    }

    @Override
    public final long getLongAt(int rowOrdinal, int colOrdinal) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.longAt(row, col);
    }

    @Override
    public final double getDouble(R rowKey, C colKey) {
        var row = data.rowCoordinate(rowKey);
        var col = data.colCoordinate(colKey);
        return row < 0 || col < 0 ? Double.NaN : data.doubleAt(row, col);
    }

    @Override
    public final double getDoubleAt(int rowOrdinal, int colOrdinal) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.doubleAt(row, col);
    }

    @Override
    public final <T> T getValue(R rowKey, C colKey) {
        var row = data.rowCoordinate(rowKey);
        var col = data.colCoordinate(colKey);
        return row < 0 || col < 0 ? null : data.valueAt(row, col);
    }

    @Override
    public final <T> T getValueAt(int rowOrdinal, int colOrdinal) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.valueAt(row, col);
    }

    @Override
    public final boolean setBoolean(R rowKey, C colKey, boolean value) {
        var row = data.rowCoordinateOrFail(rowKey);
        var col = data.colCoordinateOrFail(colKey);
        return data.booleanAt(row, col, value);
    }

    @Override
    public final boolean setBooleanAt(int rowOrdinal, int colOrdinal, boolean value) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.booleanAt(row, col, value);
    }

    @Override
    public final int setInt(R rowKey, C colKey, int value) {
        var row = data.rowCoordinateOrFail(rowKey);
        var col = data.colCoordinateOrFail(colKey);
        return data.intAt(row, col, value);
    }

    @Override
    public final int setIntAt(int rowOrdinal, int colOrdinal, int value) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.intAt(row, col, value);
    }

    @Override
    public final long setLong(R rowKey, C colKey, long value) {
        var row = data.rowCoordinateOrFail(rowKey);
        var col = data.colCoordinateOrFail(colKey);
        return data.longAt(row, col, value);
    }

    @Override
    public final long setLongAt(int rowOrdinal, int colOrdinal, long value) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.longAt(row, col, value);
    }

    @Override
    public final double setDouble(R rowKey, C colKey, double value) {
        var row = data.rowCoordinateOrFail(rowKey);
        var col = data.colCoordinateOrFail(colKey);
        return data.doubleAt(row, col, value);
    }

    @Override
    public final double setDoubleAt(int rowOrdinal, int colOrdinal, double value) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.doubleAt(row, col, value);
    }

    @Override
    public final <T> T setValue(R rowKey, C colKey, T value) {
        var row = data.rowCoordinateOrFail(rowKey);
        var col = data.colCoordinateOrFail(colKey);
        return data.valueAt(row, col, value);
    }

    @Override
    public final <T> T setValueAt(int rowOrdinal, int colOrdinal, T value) {
        var row = data.rowCoordinateAt(rowOrdinal);
        var col = data.colCoordinateAt(colOrdinal);
        return data.valueAt(row, col, value);
    }


    @Override()
    public Stream<DataFrameValue<R,C>> values() {
        if (isEmpty()) {
            return Stream.empty();
        } else {
            var valueCount = rowCount() * colCount();
            var splitThreshold = Math.max(valueCount, valueCount / Runtime.getRuntime().availableProcessors());
            return StreamSupport.stream(new DataFrameValueSpliterator<>(0, valueCount-1, rowCount(), splitThreshold), isParallel());
        }
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final boolean equals(Object object) {
        if (!(object instanceof DataFrame)) {
            return false;
        } else {
            var rowCount = rowCount();
            var colCount = colCount();
            var other = (DataFrame<R,C>)object;
            if (other.rowCount() != rowCount || other.colCount() != colCount) {
                return false;
            } else {
                for (int i=0; i<rowCount; ++i) {
                    var rowKey1 = rows().key(i);
                    var rowKey2 = other.rows().key(i);
                    if (!rowKey1.equals(rowKey2)) {
                        return false;
                    }
                }
                for (int j=0; j<colCount; ++j) {
                    var colKey1 = cols().key(j);
                    var colKey2 = other.cols().key(j);
                    if (!colKey1.equals(colKey2)) {
                        return false;
                    }
                }
                var left = this.cursor();
                var right = other.cursor();
                for (int i=0; i<rowCount; ++i) {
                    left.rowAt(i);
                    right.rowAt(i);
                    for (int j=0; j<colCount; ++j) {
                        left.colAt(j);
                        right.colAt(j);
                        if (!left.isEqualTo(right.getValue())) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
    }


    @Override()
    public String toString() {
        var rowCount = rows.count();
        var colCount = cols.count();
        var text = new StringBuilder();
        text.append("DataFrame[").append(rowCount).append("x").append(colCount).append("]");
        if (rowCount > 0) {
            text.append(" rows=[");
            text.append(rows.firstKey().orElse(null));
            text.append("...");
            text.append(rows.lastKey().orElse(null));
            text.append("]");
        }
        if (colCount > 0) {
            text.append(", columns=[");
            text.append(cols.firstKey().orElse(null));
            text.append("...");
            text.append(cols.lastKey().orElse(null));
            text.append("]");
        }
        return text.toString();
    }


    /**
     * Custom object serialization method for improved performance
     * @param os    the output stream
     * @throws IOException  if write fails
     */
    private void writeObject(ObjectOutputStream os) throws IOException {
        os.writeObject(data);
    }


    /**
     * Custom object serialization method for improved performance
     * @param is    the input stream
     * @throws IOException  if read fails
     * @throws ClassNotFoundException   if read fails
     */
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream is) throws IOException, ClassNotFoundException {
        this.data = (XDataFrameContent)is.readObject();
        this.events = new XDataFrameEvents();
        this.rows = new XDataFrameRows<>(this, false);
        this.cols = new XDataFrameColumns<>(this, false);
    }


    /**
     * A RecursiveAction to apply booleans in a DataFrame
     */
    private class ApplyBooleans extends RecursiveAction {

        private int from;
        private int to;
        private int threshold;
        private ToBooleanFunction<DataFrameValue<R,C>> mapper;

        /**
         * Constructor
         * @param from      the from column index in view space
         * @param to        the to column index in view space
         * @param threshold the threshold to trigger parallelism
         * @param mapper    the mapper function
         */
        ApplyBooleans(int from, int to, int threshold, ToBooleanFunction<DataFrameValue<R,C>> mapper) {
            this.from = from;
            this.to = to;
            this.threshold = threshold;
            this.mapper = mapper;
        }

        @Override
        protected void compute() {
            var count = (to - from) + 1;
            if (count > threshold) {
                var midPoint = from + ((to - from) / 2);
                invokeAll(
                    new ApplyBooleans(from, midPoint, threshold, mapper),
                    new ApplyBooleans(midPoint+1, to, threshold, mapper)
                );
            } else {
                var rowCount = rowCount();
                var value = cursor();
                for (int index=from; index<=to; ++index) {
                    var rowOrdinal = index % rowCount;
                    var colOrdinal = index / rowCount;
                    value.atOrdinals(rowOrdinal, colOrdinal);
                    var result = mapper.applyAsBoolean(value);
                    value.setBoolean(result);
                }
            }
        }
    }


    /**
     * A RecursiveAction to apply ints in a DataFrame
     */
    private class ApplyInts extends RecursiveAction {

        private int from;
        private int to;
        private int threshold;
        private ToIntFunction<DataFrameValue<R,C>> mapper;

        /**
         * Constructor
         * @param from      the from column index in view space
         * @param to        the to column index in view space
         * @param threshold the threshold to trigger parallelism
         * @param mapper    the mapper function
         */
        ApplyInts(int from, int to, int threshold, ToIntFunction<DataFrameValue<R,C>> mapper) {
            this.from = from;
            this.to = to;
            this.threshold = threshold;
            this.mapper = mapper;
        }

        @Override
        protected void compute() {
            var count = (to - from) + 1;
            if (count > threshold) {
                var midPoint = from + ((to - from) / 2);
                invokeAll(
                    new ApplyInts(from, midPoint, threshold, mapper),
                    new ApplyInts(midPoint+1, to, threshold, mapper)
                );
            } else {
                var rowCount = rowCount();
                var value = cursor();
                for (int index=from; index<=to; ++index) {
                    var rowOrdinal = index % rowCount;
                    var colOrdinal = index / rowCount;
                    value.atOrdinals(rowOrdinal, colOrdinal);
                    var result = mapper.applyAsInt(value);
                    value.setInt(result);
                }
            }
        }
    }


    /**
     * A RecursiveAction to apply longs in a DataFrame
     */
    private class ApplyLongs extends RecursiveAction {

        private int from;
        private int to;
        private int threshold;
        private ToLongFunction<DataFrameValue<R,C>> mapper;

        /**
         * Constructor
         * @param from      the from column index in view space
         * @param to        the to column index in view space
         * @param threshold the threshold to trigger parallelism
         * @param mapper    the mapper function
         */
        ApplyLongs(int from, int to, int threshold, ToLongFunction<DataFrameValue<R,C>> mapper) {
            this.from = from;
            this.to = to;
            this.threshold = threshold;
            this.mapper = mapper;
        }

        @Override
        protected void compute() {
            var count = (to - from) + 1;
            if (count > threshold) {
                var midPoint = from + ((to - from) / 2);
                invokeAll(
                    new ApplyLongs(from, midPoint, threshold, mapper),
                    new ApplyLongs(midPoint+1, to, threshold, mapper)
                );
            } else {
                var rowCount = rowCount();
                var value = cursor();
                for (int index=from; index<=to; ++index) {
                    var rowOrdinal = index % rowCount;
                    var colOrdinal = index / rowCount;
                    value.atOrdinals(rowOrdinal, colOrdinal);
                    var result = mapper.applyAsLong(value);
                    value.setLong(result);
                }
            }
        }
    }


    /**
     * A RecursiveAction to apply doubles in a DataFrame
     */
    private class ApplyDoubles extends RecursiveAction {

        private int from;
        private int to;
        private int threshold;
        private ToDoubleFunction<DataFrameValue<R,C>> mapper;

        /**
         * Constructor
         * @param from      the from coordinate
         * @param to        the to coordinate
         * @param threshold the threshold to trigger parallelism
         * @param mapper    the mapper function
         */
        ApplyDoubles(int from, int to, int threshold, ToDoubleFunction<DataFrameValue<R,C>> mapper) {
            this.from = from;
            this.to = to;
            this.threshold = threshold;
            this.mapper = mapper;
        }

        @Override
        protected void compute() {
            var count = (to - from) + 1;
            if (count > threshold) {
                var midPoint = from + ((to - from) / 2);
                invokeAll(
                    new ApplyDoubles(from, midPoint, threshold, mapper),
                    new ApplyDoubles(midPoint+1, to, threshold, mapper)
                );
            } else {
                var rowCount = rowCount();
                var value = cursor();
                for (int index=from; index<=to; ++index) {
                    var rowOrdinal = index % rowCount;
                    var colOrdinal = index / rowCount;
                    value.atOrdinals(rowOrdinal, colOrdinal);
                    var result = mapper.applyAsDouble(value);
                    value.setDouble(result);
                }
            }
        }
    }


    /**
     * A RecursiveAction to apply objects in a DataFrame
     */
    private class ApplyValues extends RecursiveAction {

        private int from;
        private int to;
        private int threshold;
        private Function<DataFrameValue<R,C>,?> mapper;

        /**
         * Constructor
         * @param from      the from column index in view space
         * @param to        the to column index in view space
         * @param threshold the threshold to trigger parallelism
         * @param mapper    the mapper function
         */
        ApplyValues(int from, int to, int threshold, Function<DataFrameValue<R,C>,?> mapper) {
            this.from = from;
            this.to = to;
            this.threshold = threshold;
            this.mapper = mapper;
        }

        @Override
        protected void compute() {
            var count = (to - from) + 1;
            if (count > threshold) {
                var midPoint = from + ((to - from) / 2);
                invokeAll(
                    new ApplyValues(from, midPoint, threshold, mapper),
                    new ApplyValues(midPoint+1, to, threshold, mapper)
                );
            } else {
                var rowCount = rowCount();
                var value = cursor();
                for (int index=from; index<=to; ++index) {
                    var rowOrdinal = index % rowCount;
                    var colOrdinal = index / rowCount;
                    value.atOrdinals(rowOrdinal, colOrdinal);
                    var result = mapper.apply(value);
                    value.setValue(result);
                }
            }
        }
    }


    /**
     * A RecursiveAction to iterate through all values in a DataFrame
     */
    private class ForEachValue extends RecursiveAction {

        private int from;
        private int to;
        private int threshold;
        private Consumer<DataFrameValue<R,C>> consumer;

        /**
         * Constructor
         * @param from      the from column index in view space
         * @param to        the to column index in view space
         * @param threshold the threshold to trigger parallelism
         * @param consumer    the mapper function
         */
        ForEachValue(int from, int to, int threshold, Consumer<DataFrameValue<R,C>> consumer) {
            this.from = from;
            this.to = to;
            this.threshold = threshold;
            this.consumer = consumer;
        }

        @Override
        protected void compute() {
            var count = (to - from) + 1;
            if (count > threshold) {
                var midPoint = from + ((to - from) / 2);
                invokeAll(
                    new ForEachValue(from, midPoint, threshold, consumer),
                    new ForEachValue(midPoint+1, to, threshold, consumer)
                );
            } else {
                var rowCount = rowCount();
                var cursor = cursor();
                for (int index=from; index<=to; ++index) {
                    var rowOrdinal = index % rowCount;
                    var colOrdinal = index / rowCount;
                    cursor.atOrdinals(rowOrdinal, colOrdinal);
                    consumer.accept(cursor);
                }
            }
        }
    }



    /**
     * A Spliterator implementation to iterate over all values in a DataFrame.
     * @param <X>   the row key type
     * @param <Y>   the column key type
     */
    private class DataFrameValueSpliterator<X,Y> implements Spliterator<DataFrameValue<X,Y>> {

        private int position;
        private int start;
        private int end;
        private int rowCount;
        private int splitThreshold;
        private DataFrameCursor<X,Y> value;

        /**
         * Constructor
         * @param start             the start ordinal
         * @param end               the end ordinal
         * @param rowCount          the row count of frame when Spliterator was originally created
         * @param splitThreshold    the split threshold
         */
        @SuppressWarnings("unchecked")
        private DataFrameValueSpliterator(int start, int end, int rowCount, int splitThreshold) {
            Asserts.check(start <= end, "The from ordinal must be <= the to oridinal");
            Asserts.check(splitThreshold > 0, "The split threshold must be > 0");
            this.position = start;
            this.start = start;
            this.end = end;
            this.rowCount = rowCount;
            this.splitThreshold = splitThreshold;
            this.value = (DataFrameCursor<X,Y>)cursor();
        }

        @Override
        public boolean tryAdvance(Consumer<? super DataFrameValue<X,Y>> action) {
            Asserts.check(action != null, "The consumer action cannot be null");
            if (position <= end) {
                var rowOrdinal = position % rowCount;
                var colOrdinal = position / rowCount;
                this.value.atOrdinals(rowOrdinal, colOrdinal);
                this.position++;
                action.accept(value);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Spliterator<DataFrameValue<X,Y>> trySplit() {
            if (estimateSize() < splitThreshold) {
                return null;
            } else {
                var newStart = start;
                var halfSize = (end - start) / 2;
                var newEnd = newStart + halfSize;
                this.start = newEnd + 1;
                this.position = start;
                return new DataFrameValueSpliterator<>(newStart, newEnd, rowCount, splitThreshold);
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
     * A RecursiveTask to select row keys that match a user provided predicate
     */
    private class SelectRows extends RecursiveTask<Array<R>> {

        private int from;
        private int to;
        private int threshold;
        private Predicate<DataFrameRow<R,C>> predicate;

        /**
         * Constructor
         * @param from      the from ordinal
         * @param to        the to row index in view space
         * @param predicate the predicate to match rows
         */
        SelectRows(int from, int to, Predicate<DataFrameRow<R,C>> predicate) {
            this.from = from;
            this.to = to;
            this.predicate = predicate;
            this.threshold = Integer.MAX_VALUE;
            if (isParallel()) {
                this.threshold = DataFrameOptions.getRowSplitThreshold(XDataFrame.this);
            }
        }

        @Override
        protected Array<R> compute() {
            var count = to - from + 1;
            final Class<R> keyType = rows().keyClass();
            if (count > threshold) {
                return split();
            } else {
                var rowCount = rowCount();
                var row = new XDataFrameRow<R,C>(XDataFrame.this, false);
                var builder = ArrayBuilder.of(rowCount > 0 ? rowCount : 10, keyType);
                for (int ordinal=from; ordinal<=to; ++ordinal) {
                    row.atOrdinal(ordinal);
                    if (predicate.test(row)) {
                        builder.append(row.key());
                    }
                }
                return builder.toArray();
            }
        }

        /**
         * Splits this task into two sub-tasks and executes them in parallel
         * @return  the join results from the two sub-tasks
         */
        private Array<R> split() {
            var splitCount = (to - from) / 2;
            var midPoint = from + splitCount;
            var left  = new SelectRows(from, midPoint, predicate);
            var right = new SelectRows(midPoint + 1, to, predicate);
            left.fork();
            var rightAns = right.compute();
            var leftAns  = left.join();
            var size = Math.max(rightAns.length() + leftAns.length(), 10);
            var rowKeyType = rows().keyClass();
            var builder = ArrayBuilder.of(size, rowKeyType);
            builder.appendAll(leftAns);
            builder.appendAll(rightAns);
            return builder.toArray();
        }
    }


    /**
     * A RecursiveTask to select column keys that match a user provided predicate
     */
    private class SelectColumns extends RecursiveTask<Array<C>> {

        private int from;
        private int to;
        private int threshold;
        private Predicate<DataFrameColumn<R,C>> predicate;

        /**
         * Constructor
         * @param from      the from ordinal
         * @param to        the to col ordinal
         * @param predicate the predicate to match columns
         */
        SelectColumns(int from, int to, Predicate<DataFrameColumn<R,C>> predicate) {
            this.from = from;
            this.to = to;
            this.predicate = predicate;
            this.threshold = Integer.MAX_VALUE;
            if (isParallel()) {
                this.threshold = DataFrameOptions.getColumnSplitThreshold(XDataFrame.this);
            }
        }

        @Override
        protected Array<C> compute() {
            var count = to - from + 1;
            var keyType = cols().keyClass();
            if (count > threshold) {
                return split();
            } else {
                var colCount = colCount();
                var column = new XDataFrameColumn<R,C>(XDataFrame.this, false);
                var builder = ArrayBuilder.of(colCount > 0 ? colCount : 10, keyType);
                for (int ordinal=from; ordinal<=to; ++ordinal) {
                    column.atOrdinal(ordinal);
                    if (predicate.test(column)) {
                        builder.append(column.key());
                    }
                }
                return builder.toArray();
            }
        }

        /**
         * Splits this task into two sub-tasks and executes them in parallel
         * @return  the join results from the two sub-tasks
         */
        private Array<C> split() {
            var splitCount = (to - from) / 2;
            var midPoint = from + splitCount;
            var left  = new SelectColumns(from, midPoint, predicate);
            var right = new SelectColumns(midPoint + 1, to, predicate);
            left.fork();
            var rightAns = right.compute();
            var leftAns  = left.join();
            var size = Math.max(rightAns.length() + leftAns.length(), 10);
            var builder = ArrayBuilder.of(size, cols().keyClass());
            builder.appendAll(leftAns);
            builder.appendAll(rightAns);
            return builder.toArray();
        }
    }

    /**
     * A task to find the min value in the DataFrame
     */
    private class MinMaxValueTask extends RecursiveTask<Optional<DataFrameValue<R,C>>> {

        private int offset;
        private int length;
        private boolean min;
        private int threshold = Integer.MAX_VALUE;
        private Predicate<DataFrameValue<R,C>> predicate;

        /**
         * Constructor
         * @param offset    the offset ordinal
         * @param length    the number of items for this task
         * @param min       if true, task finds the min value, else the max value
         * @param predicate the predicate to filter on values
         */
        MinMaxValueTask(int offset, int length, boolean min, Predicate<DataFrameValue<R,C>> predicate) {
            this.offset = offset;
            this.length = length;
            this.min = min;
            this.predicate = predicate;
            if (isParallel() && rowCount() > colCount()) {
                this.threshold = DataFrameOptions.getRowSplitThreshold(XDataFrame.this);
            } else if (isParallel()) {
                this.threshold = DataFrameOptions.getColumnSplitThreshold(XDataFrame.this);
            }
        }

        @Override
        protected Optional<DataFrameValue<R,C>> compute() {
            if (length > threshold) {
                return split();
            } else if (rowCount() > colCount()) {
                return initial().map(result -> {
                    var value = cursor();
                    var rowStart = result.rowOrdinal() - offset;
                    for (int i=rowStart; i<length; ++i) {
                        value.rowAt(offset + i);
                        var colStart = i == rowStart ? result.colOrdinal() : 0;
                        for (int j=colStart; j<colCount(); ++j) {
                            value.colAt(j);
                            if (predicate.test(value)) {
                                if (min && value.compareTo(result) < 0) {
                                    result.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                } else if (!min && value.compareTo(result) > 0) {
                                    result.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                }
                            }
                        }
                    }
                    return result;
                });
            } else {
                return initial().map(result -> {
                    var value = cursor();
                    var colStart = result.colOrdinal() - offset;
                    for (int i=colStart; i<length; ++i) {
                        value.colAt(offset + i);
                        var rowStart = i == colStart ? result.rowOrdinal() : 0;
                        for (int j=rowStart; j<rowCount(); ++j) {
                            value.rowAt(j);
                            if (predicate.test(value)) {
                                if (min && value.compareTo(result) < 0) {
                                    result.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                } else if (!min && value.compareTo(result) > 0) {
                                    result.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                }
                            }
                        }
                    }
                    return result;
                });
            }
        }

        /**
         * Initializes the result cursor to an appropriate starting point
         * @return      the initial result cursor to track min value
         */
        private Optional<DataFrameCursor<R,C>> initial() {
            var result = cursor();
            if (rowCount() > colCount()) {
                result.atOrdinals(offset, 0);
                for (int i=0; i<length; ++i) {
                    if (predicate.test(result)) break;
                    result.rowAt(offset + i);
                    for (int j=0; j<colCount(); ++j) {
                        result.colAt(j);
                        if (predicate.test(result)) {
                            break;
                        }
                    }
                }
            } else {
                result.atOrdinals(0, offset);
                for (int i=0; i<length; ++i) {
                    if (predicate.test(result)) break;
                    result.colAt(offset + i);
                    for (int j=0; j<rowCount(); ++j) {
                        result.rowAt(j);
                        if (predicate.test(result)) {
                            break;
                        }
                    }
                }
            }
            if (predicate.test(result)) {
                return Optional.of(result);
            } else {
                return Optional.empty();
            }
        }

        /**
         * Splits this task into two and computes min across the two tasks
         * @return      returns the min across the two split tasks
         */
        private Optional<DataFrameValue<R,C>> split() {
            var splitLength = length / 2;
            var midPoint = offset + splitLength;
            var leftTask = new MinMaxValueTask(offset, splitLength, min, predicate);
            var rightTask = new MinMaxValueTask(midPoint, length - splitLength, min, predicate);
            leftTask.fork();
            var rightAns = rightTask.compute();
            var leftAns = leftTask.join();
            if (leftAns.isPresent() && rightAns.isPresent()) {
                var left = leftAns.get();
                var right = rightAns.get();
                var result = left.compareTo(right);
                return min ? result < 0 ? leftAns : rightAns : result > 0 ? leftAns : rightAns;
            } else {
                return leftAns.isPresent() ? leftAns : rightAns;
            }
        }
    }



    /**
     * A task to find the upper/lower bounds of the DataFrame
     */
    private class BoundsTask<V> extends RecursiveTask<Optional<Bounds<V>>> {

        private int offset;
        private int length;
        private int threshold = Integer.MAX_VALUE;
        private Predicate<DataFrameValue<R,C>> predicate;

        /**
         * Constructor
         * @param offset    the offset ordinal
         * @param length    the number of items for this task
         * @param predicate the predicate to filter on values
         */
        BoundsTask(int offset, int length, Predicate<DataFrameValue<R,C>> predicate) {
            this.offset = offset;
            this.length = length;
            this.predicate = predicate;
            if (isParallel() && rowCount() > colCount()) {
                this.threshold = DataFrameOptions.getRowSplitThreshold(XDataFrame.this);
            } else if (isParallel()) {
                this.threshold = DataFrameOptions.getColumnSplitThreshold(XDataFrame.this);
            }
        }

        @Override
        protected Optional<Bounds<V>> compute() {
            if (length > threshold) {
                return split();
            } else if (rowCount() > colCount()) {
                return initial().map(initial -> {
                    var value = cursor();
                    var min = initial.copy();
                    var max = initial.copy();
                    var rowStart = initial.rowOrdinal() - offset;
                    for (int i=rowStart; i<length; ++i) {
                        value.rowAt(offset + i);
                        var colStart = i == rowStart ? initial.colOrdinal() : 0;
                        for (int j=colStart; j<colCount(); ++j) {
                            value.colAt(j);
                            if (predicate.test(value)) {
                                if (value.compareTo(min) < 0) {
                                    min.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                } else if (value.compareTo(max) > 0) {
                                    max.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                }
                            }
                        }
                    }
                    var lower = min.<V>getValue();
                    var upper = max.<V>getValue();
                    return Bounds.of(lower, upper);
                });
            } else {
                return initial().map(initial -> {
                    var value = cursor();
                    var min = initial.copy();
                    var max = initial.copy();
                    var colStart = initial.colOrdinal() - offset;
                    for (int i=colStart; i<length; ++i) {
                        value.colAt(offset + i);
                        var rowStart = i == colStart ? initial.rowOrdinal() : 0;
                        for (int j=rowStart; j<rowCount(); ++j) {
                            value.rowAt(j);
                            if (predicate.test(value)) {
                                if (value.compareTo(min) < 0) {
                                    min.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                } else if (value.compareTo(max) > 0) {
                                    max.atOrdinals(value.rowOrdinal(), value.colOrdinal());
                                }
                            }
                        }
                    }
                    var lower = min.<V>getValue();
                    var upper = max.<V>getValue();
                    return Bounds.of(lower, upper);
                });
            }
        }

        /**
         * Initializes the result cursor to an appropriate starting point
         * @return      the initial result cursor to track min value
         */
        private Optional<DataFrameCursor<R,C>> initial() {
            var result = cursor();
            if (rowCount() > colCount()) {
                result.atOrdinals(offset, 0);
                for (int i=0; i<length; ++i) {
                    if (predicate.test(result)) break;
                    result.rowAt(offset + i);
                    for (int j=0; j<colCount(); ++j) {
                        result.colAt(j);
                        if (predicate.test(result)) {
                            break;
                        }
                    }
                }
            } else {
                result.atOrdinals(0, offset);
                for (int i=0; i<length; ++i) {
                    if (predicate.test(result)) break;
                    result.colAt(offset + i);
                    for (int j=0; j<rowCount(); ++j) {
                        result.rowAt(j);
                        if (predicate.test(result)) {
                            break;
                        }
                    }
                }
            }
            if (predicate.test(result)) {
                return Optional.of(result);
            } else {
                return Optional.empty();
            }
        }

        /**
         * Splits this task into two and computes min across the two tasks
         * @return      returns the min across the two split tasks
         */
        private Optional<Bounds<V>> split() {
            var splitLength = length / 2;
            var midPoint = offset + splitLength;
            var leftTask = new BoundsTask<V>(offset, splitLength, predicate);
            var rightTask = new BoundsTask<V>(midPoint, length - splitLength, predicate);
            leftTask.fork();
            var rightAns = rightTask.compute();
            var leftAns = leftTask.join();
            if (leftAns.isPresent() && rightAns.isPresent()) {
                var left = leftAns.get();
                var right = rightAns.get();
                return Optional.of(Bounds.ofAll(left, right));
            } else {
                return leftAns.isPresent() ? leftAns : rightAns;
            }
        }
    }



    /**
     * The double iterator over all numeric values in the frame
     */
    private class DoubleIterator implements PrimitiveIterator.OfDouble {

        private DataFrameValue<R,C> value;
        private Iterator<DataFrameValue<R,C>> iterator;

        /**
         * Constructor
         * @param iterator  the input iterator for this double iterator
         */
        DoubleIterator(Iterator<DataFrameValue<R,C>> iterator) {
            this.iterator = iterator;
        }

        @Override
        public double nextDouble() {
            return value.getDouble();
        }

        @Override
        public boolean hasNext() {
            while (iterator.hasNext()) {
                this.value = iterator.next();
                if (value.isNumeric()) {
                    return true;
                }
            }
            return false;
        }
    }

}
