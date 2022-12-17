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
package com.d3x.morpheus.array.dense;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.function.Predicate;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBase;
import com.d3x.morpheus.array.ArrayBuilder;
import com.d3x.morpheus.array.ArrayCursor;
import com.d3x.morpheus.array.ArrayException;
import com.d3x.morpheus.array.ArrayStyle;
import com.d3x.morpheus.array.ArrayValue;
import com.d3x.morpheus.array.coding.IntCoding;
import com.d3x.morpheus.array.coding.WithIntCoding;
import org.eclipse.collections.impl.factory.primitive.IntSets;

/**
 * A dense array implementation that maintains a primitive int array of codes that apply to Object values exposed through the IntCoding interface.
 *
 * @author Xavier Witdouck
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 */
class DenseArrayWithIntCoding<T> extends ArrayBase<T> implements WithIntCoding<T> {

    private static final long serialVersionUID = 1L;

    private int[] codes;
    private T defaultValue;
    private int defaultCode;
    private IntCoding<T> coding;

    /**
     * Constructor
     * @param length        the length for this array
     * @param defaultValue  the default value for array
     * @param coding        the coding for this array
     */
    DenseArrayWithIntCoding(int length, T defaultValue, IntCoding<T> coding) {
        super(coding.getType(), ArrayStyle.DENSE, false);
        this.coding = coding;
        this.codes = new int[length];
        this.defaultValue = defaultValue;
        this.defaultCode = coding.getCode(defaultValue);
        Arrays.fill(codes, defaultCode);
    }


    /**
     * Constructor
     * @param source    the source array to copy
     * @param parallel  true for the parallel version
     */
    private DenseArrayWithIntCoding(DenseArrayWithIntCoding<T> source, boolean parallel) {
        super(source.type(), ArrayStyle.DENSE, parallel);
        this.coding = source.coding;
        this.codes = source.codes;
        this.defaultValue = source.defaultValue;
        this.defaultCode = source.defaultCode;
    }


    @Override
    public final IntCoding<T> getCoding() {
        return coding;
    }


    @Override
    public final int length() {
        return codes.length;
    }


    @Override
    public float loadFactor() {
        return 1F;
    }


    @Override
    public final T defaultValue() {
        return defaultValue;
    }


    @Override
    public final Array<T> parallel() {
        return isParallel() ? this : new DenseArrayWithIntCoding<>(this, true);
    }


    @Override
    public final Array<T> sequential() {
        return isParallel() ? new DenseArrayWithIntCoding<>(this, false) : this;
    }


    @Override()
    @SuppressWarnings("unchecked")
    public final Array<T> copy() {
        try {
            final DenseArrayWithIntCoding<T> copy = (DenseArrayWithIntCoding<T>)super.clone();
            copy.defaultValue = this.defaultValue;
            copy.defaultCode = this.defaultCode;
            copy.coding = this.coding;
            copy.codes = this.codes.clone();
            return copy;
        } catch (Exception ex) {
            throw new ArrayException("Failed to copy Array: " + this, ex);
        }
    }


    @Override()
    public final Array<T> copy(int[] indexes) {
        var clone = new DenseArrayWithIntCoding<T>(indexes.length, defaultValue, coding);
        for (int i = 0; i < indexes.length; ++i) {
            clone.codes[i] = this.codes[indexes[i]];
        }
        return clone;
    }


    @Override
    public Array<T> copy(Array<Integer> indexes) {
        var clone = new DenseArrayWithIntCoding<T>(indexes.length(), defaultValue, coding);
        for (int i = 0; i < indexes.length(); ++i) {
            clone.codes[i] = this.codes[indexes.getInt(i)];
        }
        return clone;
    }


    @Override()
    public final Array<T> copy(int start, int end) {
        var length = end - start;
        final DenseArrayWithIntCoding<T> clone = new DenseArrayWithIntCoding<>(length, defaultValue, coding);
        System.arraycopy(codes, start, clone.codes, 0, length);
        return clone;
    }


    @Override
    protected final Array<T> sort(int start, int end, int multiplier) {
        return doSort(start, end, (i, j) -> multiplier * Integer.compare(codes[i], codes[j]));
    }


    @Override
    public final int compare(int i, int j) {
        return Integer.compare(codes[i], codes[j]);
    }


    @Override
    public final Array<T> swap(int i, int j) {
        var v1 = codes[i];
        var v2 = codes[j];
        this.codes[i] = v2;
        this.codes[j] = v1;
        return this;
    }


    @Override
    public final Array<T> filter(Predicate<ArrayValue<T>> predicate) {
        final ArrayCursor<T> cursor = cursor();
        final ArrayBuilder<T> builder = ArrayBuilder.of(length(), type());
        for (int i = 0; i< length(); ++i) {
            cursor.moveTo(i);
            final boolean match = predicate.test(cursor);
            if (match) {
                builder.append(cursor.getValue());
            }
        }
        return builder.toArray();
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
        if (from instanceof DenseArrayWithIntCoding) {
            final DenseArrayWithIntCoding other = (DenseArrayWithIntCoding) from;
            for (int i = 0; i < length; ++i) {
                this.codes[toIndex + i] = other.codes[fromIndex + i];
            }
        } else if (from instanceof DenseArrayOfInts) {
            for (int i = 0; i < length; ++i) {
                this.codes[toIndex + i] = from.getInt(fromIndex + i);
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
        if (newLength > codes.length) {
            var newCodes = new int[newLength];
            System.arraycopy(codes, 0, newCodes, 0, codes.length);
            Arrays.fill(newCodes, codes.length, newCodes.length, defaultCode);
            this.codes = newCodes;
        }
        return this;
    }


    @Override
    public Array<T> fill(T value, int start, int end) {
        var code = coding.getCode(value);
        Arrays.fill(codes, start, end, code);
        return this;
    }


    @Override
    public final boolean isNull(int index) {
        return codes[index] == coding.getCode(null);
    }


    @Override
    public final boolean isEqualTo(int index, T value) {
        if (value == null) {
            return isNull(index);
        } else {
            var code = coding.getCode(value);
            return code == codes[index];
        }
    }


    @Override
    public int getInt(int index) {
        return codes[index];
    }


    @Override
    public final T getValue(int index) {
        return coding.getValue(codes[index]);
    }


    @Override
    public int setInt(int index, int value) {
        var oldValue = getInt(index);
        this.codes[index] = value;
        return oldValue;
    }


    @Override
    public final T setValue(int index, T value) {
        final T oldValue = getValue(index);
        this.codes[index] = coding.getCode(value);
        return oldValue;
    }


    @Override
    public Array<T> distinct(int limit) {
        var capacity = limit < Integer.MAX_VALUE ? limit : 100;
        var set = IntSets.mutable.withInitialCapacity(capacity);
        var builder = ArrayBuilder.<T>of(capacity, type());
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
            this.codes[i] = is.readInt();
        }
    }


    @Override
    public final void write(ObjectOutputStream os, int[] indexes) throws IOException {
        for (int index : indexes) {
            os.writeInt(codes[index]);
        }
    }

    /** Custom serialization */
    private void writeObject(ObjectOutputStream os) throws IOException {
        os.writeObject(coding);
        os.writeInt(codes.length);
        for (int value : codes) {
            os.writeInt(value);
        }
    }


    @SuppressWarnings("unchecked")
    /** Custom serialization */
    private void readObject(ObjectInputStream is) throws IOException, ClassNotFoundException {
        this.coding = (IntCoding<T>)is.readObject();
        var length = is.readInt();
        this.codes = new int[length];
        for (int i=0; i<length; ++i) {
            codes[i] = is.readInt();
        }
    }

}
