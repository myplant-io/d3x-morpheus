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

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBase;
import com.d3x.morpheus.array.ArrayBuilder;
import com.d3x.morpheus.array.ArrayCursor;
import com.d3x.morpheus.array.ArrayException;
import com.d3x.morpheus.array.ArrayStyle;
import com.d3x.morpheus.array.ArrayValue;
import com.d3x.morpheus.array.coding.IntCoding;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntSets;

/**
 * A sparse array implementation that maintains a primitive int array of codes that apply to Object values exposed through the Coding interface.
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
class SparseArrayWithIntCoding<T> extends ArrayBase<T> {

    private static final long serialVersionUID = 1L;

    private int length;
    private T defaultValue;
    private int defaultCode;
    private MutableIntIntMap codes;
    private IntCoding<T> coding;


    /**
     * Constructor
     * @param length        the length for this array
     * @param fillPct   the fill percent for array (0.2 implies 20% filled)
     * @param defaultValue  the default value for array
     * @param coding        the coding for this array
     */
    SparseArrayWithIntCoding(int length, float fillPct, T defaultValue, IntCoding<T> coding) {
        super(coding.getType(), ArrayStyle.SPARSE, false);
        this.length = length;
        this.coding = coding;
        this.defaultValue = defaultValue;
        this.defaultCode = coding.getCode(defaultValue);
        this.codes = IntIntMaps.mutable.withInitialCapacity((int)Math.max(length * fillPct, 5d));
    }

    /**
     * Constructor
     * @param source    the source array to shallow copy
     * @param parallel  true for the parallel version
     */
    private SparseArrayWithIntCoding(SparseArrayWithIntCoding<T> source, boolean parallel) {
        super(source.type(), ArrayStyle.SPARSE, parallel);
        this.length = source.length;
        this.coding = source.coding;
        this.defaultValue = source.defaultValue;
        this.defaultCode = source.defaultCode;
        this.codes = source.codes;
    }

    @Override
    public final int length() {
        return length;
    }


    @Override()
    public final float loadFactor() {
        return (float)codes.size() / (float)length();
    }


    @Override
    public final T defaultValue() {
        return defaultValue;
    }


    @Override
    public final Array<T> parallel() {
        return isParallel() ? this : new SparseArrayWithIntCoding<>(this, true);
    }


    @Override
    public final Array<T> sequential() {
        return isParallel() ? new SparseArrayWithIntCoding<>(this, false) : this;
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final Array<T> copy() {
        try {
            final SparseArrayWithIntCoding<T> copy = (SparseArrayWithIntCoding<T>)super.clone();
            copy.codes = IntIntMaps.mutable.withAll(codes);
            copy.defaultValue = this.defaultValue;
            copy.defaultCode = this.defaultCode;
            copy.coding = this.coding;
            return copy;
        } catch (Exception ex) {
            throw new ArrayException("Failed to copy Array: " + this, ex);
        }
    }


    @Override()
    public final Array<T> copy(int[] indexes) {
        var fillPct = (float)codes.size() / length();
        var clone = new SparseArrayWithIntCoding<T>(indexes.length, fillPct, defaultValue, coding);
        for (int i = 0; i < indexes.length; ++i) {
            var code = getInt(indexes[i]);
            clone.codes.put(i, code);
        }
        return clone;
    }


    @Override
    public Array<T> copy(Array<Integer> indexes) {
        var fillPct = (float)codes.size() / length();
        var clone = new SparseArrayWithIntCoding<T>(indexes.length(), fillPct, defaultValue, coding);
        for (int i = 0; i < indexes.length(); ++i) {
            var code = getInt(indexes.getInt(i));
            clone.codes.put(i, code);
        }
        return clone;
    }


    @Override()
    public final Array<T> copy(int start, int end) {
        var length = end - start;
        var fillPct = (float)codes.size() / length();
        var clone = new SparseArrayWithIntCoding<T>(length, fillPct, defaultValue, coding);
        for (int i=0; i<length; ++i) {
            var code = getInt(start+i);
            if (code != defaultCode) {
                clone.codes.put(i, code);
            }
        }
        return clone;
    }


    @Override
    public final int compare(int i, int j) {
        return Integer.compare(
            codes.getIfAbsent(i, defaultCode),
            codes.getIfAbsent(j, defaultCode)
        );
    }


    @Override
    public final Array<T> swap(int i, int j) {
        var v1 = getInt(i);
        var v2 = getInt(j);
        this.codes.put(i, v2);
        this.codes.put(j, v1);
        return this;
    }


    @Override
    public final Array<T> filter(Predicate<ArrayValue<T>> predicate) {
        int count = 0;
        var length = this.length();
        final ArrayCursor<T> cursor = cursor();
        final Array<T> matches = Array.of(type(), length, loadFactor());
        for (int i=0; i<length; ++i) {
            cursor.moveTo(i);
            final boolean match = predicate.test(cursor);
            if (match) matches.setValue(count++, cursor.getValue());
        }
        return count == length ? matches : matches.copy(0, count);
    }


    @Override
    public final Array<T> update(Array<T> from, int[] fromIndexes, int[] toIndexes) {
        if (fromIndexes.length != toIndexes.length) {
            throw new ArrayException("The from index array must have the same length as the to index array");
        } else {
            for (int i=0; i<fromIndexes.length; ++i) {
                var toIndex = toIndexes[i];
                var fromIndex = fromIndexes[i];
                final T update = from.getValue(fromIndex);
                this.setValue(toIndex, update);
            }
        }
        return this;
    }


    @Override
    public final Array<T> update(int toIndex, Array<T> from, int fromIndex, int length) {
        if (from instanceof SparseArrayWithIntCoding) {
            var other = (SparseArrayWithIntCoding<T>)from;
            for (int i = 0; i < length; ++i) {
                this.codes.put(toIndex + i, other.codes.getIfAbsent(fromIndex + i, defaultCode));
            }
        } else {
            for (int i=0; i<length; ++i) {
                final T update = from.getValue(fromIndex + i);
                this.setValue(toIndex + i, update);
            }
        }
        return this;
    }


    @Override
    public final Array<T> expand(int newLength) {
        this.length = Math.max(newLength, length);
        return this;
    }


    @Override
    public Array<T> fill(T value, int start, int end) {
        var fillCode = coding.getCode(value);
        if (fillCode == defaultCode) {
            this.codes.clear();
        } else {
            for (int i=start; i<end; ++i) {
                this.codes.put(i, fillCode);
            }
        }
        return this;
    }


    @Override
    public final boolean isNull(int index) {
        return codes.getIfAbsent(index, defaultCode) == coding.getCode(null);
    }


    @Override
    public final boolean isEqualTo(int index, T value) {
        if (value == null) {
            return isNull(index);
        } else {
            var code = coding.getCode(value);
            return code == codes.getIfAbsent(index, defaultCode);
        }
    }


    @Override
    public final int getInt(int index) {
        this.checkBounds(index, length);
        return codes.getIfAbsent(index, defaultCode);
    }


    @Override
    public final T getValue(int index) {
        this.checkBounds(index, length);
        var code = codes.getIfAbsent(index, defaultCode);
        return coding.getValue(code);
    }

    @Override
    public int setInt(int index, int value) {
        var oldValue = getInt(index);
        if (value == defaultCode) {
            this.codes.remove(index);
            return oldValue;
        } else {
            this.codes.put(index, value);
            return oldValue;
        }
    }

    @Override
    public final T setValue(int index, T value) {
        final T oldValue = getValue(index);
        var code = coding.getCode(value);
        if (code == defaultCode) {
            this.codes.remove(index);
            return oldValue;
        } else {
            this.codes.put(index, code);
            return oldValue;
        }
    }


    @Override
    public Array<T> distinct(int limit) {
        var capacity = limit < Integer.MAX_VALUE ? limit : 100;
        var set = IntSets.mutable.withInitialCapacity(capacity);
        final ArrayBuilder<T> builder = ArrayBuilder.of(capacity, type());
        for (int i=0; i<length(); ++i) {
            var code = getInt(i);
            if (set.add(code)) {
                final T value = getValue(i);
                builder.append(value);
                if (set.size() >= limit) {
                    break;
                }
            }
        }
        return builder.toArray();
    }


    @Override
    public final void read(ObjectInputStream is, int count) throws IOException {
        for (int i=0; i<count; ++i) {
            var value = is.readInt();
            this.codes.put(i, value);
        }
    }


    @Override
    public final void write(ObjectOutputStream os, int[] indexes) throws IOException {
        for (int index : indexes) {
            var value = getInt(index);
            os.writeInt(value);
        }
    }

}
