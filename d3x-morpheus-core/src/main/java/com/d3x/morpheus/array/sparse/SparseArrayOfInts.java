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
package com.d3x.morpheus.array.sparse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.function.Predicate;

import com.d3x.morpheus.array.ArrayBuilder;
import com.d3x.morpheus.array.ArrayCursor;
import com.d3x.morpheus.array.ArrayException;
import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBase;
import com.d3x.morpheus.array.ArrayStyle;
import com.d3x.morpheus.array.ArrayValue;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntSets;

/**
 * An Array implementation designed to hold a sparse array of int values
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
class SparseArrayOfInts extends ArrayBase<Integer> {

    private static final long serialVersionUID = 1L;

    private int length;
    private MutableIntIntMap values;
    private int defaultValue;

    /**
     * Constructor
     * @param length    the length for this array
     * @param fillPct   the fill percent for array (0.2 implies 20% filled)
     * @param defaultValue  the default value for array
     */
    SparseArrayOfInts(int length, float fillPct, Integer defaultValue) {
        super(Integer.class, ArrayStyle.SPARSE, false);
        this.length = length;
        this.defaultValue = defaultValue != null ? defaultValue : 0;
        this.values = IntIntMaps.mutable.withInitialCapacity((int)Math.max(length * fillPct, 5d));
    }

    /**
     * Constructor
     * @param source    the source array to shallow copy
     * @param parallel  true for the parallel version
     */
    private SparseArrayOfInts(SparseArrayOfInts source, boolean parallel) {
        super(source.type(), ArrayStyle.SPARSE, parallel);
        this.length = source.length;
        this.defaultValue = source.defaultValue;
        this.values = source.values;
    }


    @Override
    public final int length() {
        return length;
    }


    @Override()
    public final float loadFactor() {
        return (float)values.size() / (float)length();
    }


    @Override
    public final Integer defaultValue() {
        return defaultValue;
    }


    @Override
    public final Array<Integer> parallel() {
        return isParallel() ? this : new SparseArrayOfInts(this, true);
    }


    @Override
    public final Array<Integer> sequential() {
        return isParallel() ? new SparseArrayOfInts(this, false) : this;
    }


    @Override()
    public final Array<Integer> copy() {
        try {
            final SparseArrayOfInts copy = (SparseArrayOfInts)super.clone();
            copy.values = IntIntMaps.mutable.withAll(values);
            copy.defaultValue = this.defaultValue;
            return copy;
        } catch (Exception ex) {
            throw new ArrayException("Failed to copy Array: " + this, ex);
        }
    }


    @Override()
    public final Array<Integer> copy(int[] indexes) {
        var fillPct = (float)values.size() / length();
        var clone = new SparseArrayOfInts(indexes.length, fillPct, defaultValue);
        for (int i = 0; i < indexes.length; ++i) {
            var value = getInt(indexes[i]);
            clone.setInt(i, value);
        }
        return clone;
    }


    @Override
    public Array<Integer> copy(Array<Integer> indexes) {
        var fillPct = (float)values.size() / length();
        var clone = new SparseArrayOfInts(indexes.length(), fillPct, defaultValue);
        for (int i = 0; i < indexes.length(); ++i) {
            var value = getInt(indexes.getInt(i));
            clone.setInt(i, value);
        }
        return clone;
    }


    @Override()
    public final Array<Integer> copy(int start, int end) {
        var length = end - start;
        var fillPct = (float)values.size() / length();
        var clone = new SparseArrayOfInts(length, fillPct, defaultValue);
        for (int i=0; i<length; ++i) {
            var value = getInt(start+i);
            if (value != defaultValue) {
                clone.setValue(i, value);
            }
        }
        return clone;
    }


    @Override
    protected final Array<Integer> sort(int start, int end, int multiplier) {
        return doSort(start, end, (i, j) -> {
            var v1 = values.getIfAbsent(i, defaultValue);
            var v2 = values.getIfAbsent(j, defaultValue);
            return multiplier * Integer.compare(v1, v2);
        });
    }


    @Override
    public final int compare(int i, int j) {
        return Integer.compare(
            values.getIfAbsent(i, defaultValue),
            values.getIfAbsent(j, defaultValue)
        );
    }


    @Override
    public final Array<Integer> swap(int i, int j) {
        var v1 = getInt(i);
        var v2 = getInt(j);
        this.setInt(i, v2);
        this.setInt(j, v1);
        return this;
    }


    @Override
    public final Array<Integer> filter(Predicate<ArrayValue<Integer>> predicate) {
        int count = 0;
        var length = this.length();
        final ArrayCursor<Integer> cursor = cursor();
        final Array<Integer> matches = Array.of(type(), length, loadFactor());  //todo: fix the length of this filter
        for (int i=0; i<length; ++i) {
            cursor.moveTo(i);
            final boolean match = predicate.test(cursor);
            if (match) matches.setInt(count++, cursor.getInt());
        }
        return count == length ? matches : matches.copy(0, count);
    }


    @Override
    public final Array<Integer> update(Array<Integer> from, int[] fromIndexes, int[] toIndexes) {
        if (fromIndexes.length != toIndexes.length) {
            throw new ArrayException("The from index array must have the same length as the to index array");
        } else {
            for (int i=0; i<fromIndexes.length; ++i) {
                var toIndex = toIndexes[i];
                final int fromIndex = fromIndexes[i];
                final int update = from.getInt(fromIndex);
                this.setInt(toIndex, update);
            }
        }
        return this;
    }


    @Override
    public final Array<Integer> update(int toIndex, Array<Integer> from, int fromIndex, int length) {
        for (int i=0; i<length; ++i) {
            final int update = from.getInt(fromIndex + i);
            this.setInt(toIndex + i, update);
        }
        return this;
    }


    @Override
    public final Array<Integer> expand(int newLength) {
        this.length = Math.max(newLength, length);
        return this;
    }


    @Override
    public Array<Integer> fill(Integer value, int start, int end) {
        final int fillValue = value == null ? defaultValue : value;
        if (fillValue == defaultValue) {
            this.values.clear();
        } else {
            for (int i=start; i<end; ++i) {
                this.values.put(i, fillValue);
            }
        }
        return this;
    }


    @Override
    public final boolean isNull(int index) {
        return false;
    }


    @Override
    public final boolean isEqualTo(int index, Integer value) {
        return value == null ? isNull(index) : value == values.getIfAbsent(index, defaultValue);
    }


    @Override
    public final int getInt(int index) {
        this.checkBounds(index, length);
        return values.getIfAbsent(index, defaultValue);
    }

    @Override
    public final long getLong(int index) {
        this.checkBounds(index, length);
        return values.getIfAbsent(index, defaultValue);
    }

    @Override
    public final double getDouble(int index) {
        this.checkBounds(index, length);
        return values.getIfAbsent(index, defaultValue);
    }

    @Override
    public final Integer getValue(int index) {
        this.checkBounds(index, length);
        return values.getIfAbsent(index, defaultValue);
    }


    @Override
    public final int setInt(int index, int value) {
        this.checkBounds(index, length);
        final int oldValue = getInt(index);
        if (value == defaultValue) {
            this.values.remove(index);
            return oldValue;
        } else {
            this.values.put(index, value);
            return oldValue;
        }
    }


    @Override
    public final Integer setValue(int index, Integer value) {
        this.checkBounds(index, length);
        var oldValue = getValue(index);
        if (value == null) {
            this.values.remove(index);
            return oldValue;
        } else {
            this.values.put(index, value);
            return oldValue;
        }
    }


    @Override
    public final int binarySearch(int start, int end, Integer value) {
        int low = start;
        int high = end - 1;
        while (low <= high) {
            final int midIndex = (low + high) >>> 1;
            final int midValue = getInt(midIndex);
            final int result = Integer.compare(midValue, value);
            if (result < 0) {
                low = midIndex + 1;
            } else if (result > 0) {
                high = midIndex - 1;
            } else {
                return midIndex;
            }
        }
        return -(low + 1);
    }


    @Override
    public final Array<Integer> distinct(int limit) {
        var capacity = limit < Integer.MAX_VALUE ? limit : 100;
        var set = IntSets.mutable.withInitialCapacity(capacity);
        var builder = ArrayBuilder.of(capacity, Integer.class);
        for (int i=0; i<length(); ++i) {
            final int value = getInt(i);
            if (set.add(value)) {
                builder.appendInt(value);
                if (set.size() >= limit) {
                    break;
                }
            }
        }
        return builder.toArray();
    }


    @Override
    public final Array<Integer> cumSum() {
        var length = length();
        final Array<Integer> result = Array.of(Integer.class, length);
        result.setInt(0, values.getIfAbsent(0, defaultValue));
        for (int i=1; i<length; ++i) {
            final int prior = result.getInt(i-1);
            final int current = values.getIfAbsent(i, defaultValue);
            result.setInt(i, prior + current);
        }
        return result;
    }


    @Override
    public final void read(ObjectInputStream is, int count) throws IOException {
        for (int i=0; i<count; ++i) {
            final int value = is.readInt();
            this.setInt(i, value);
        }
    }


    @Override
    public final void write(ObjectOutputStream os, int[] indexes) throws IOException {
        for (int index : indexes) {
            final int value = getInt(index);
            os.writeInt(value);
        }
    }

}
