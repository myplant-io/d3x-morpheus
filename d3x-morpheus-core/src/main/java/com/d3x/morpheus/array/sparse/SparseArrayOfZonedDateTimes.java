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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBase;
import com.d3x.morpheus.array.ArrayCursor;
import com.d3x.morpheus.array.ArrayException;
import com.d3x.morpheus.array.ArrayStyle;
import com.d3x.morpheus.array.ArrayValue;
import org.eclipse.collections.api.map.primitive.MutableIntLongMap;
import org.eclipse.collections.api.map.primitive.MutableIntShortMap;
import org.eclipse.collections.impl.factory.primitive.IntLongMaps;
import org.eclipse.collections.impl.factory.primitive.IntShortMaps;

/**
 * An Array implementation containing a sparse array of LocalDateTine values stored as a long of epoch milliseconds.
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
class SparseArrayOfZonedDateTimes extends ArrayBase<ZonedDateTime> {

    private static final long serialVersionUID = 1L;

    private static final Map<ZoneId,Short> zoneIdMap1 = new HashMap<>();
    private static final Map<Short,ZoneId> zoneIdMap2 = new HashMap<>();

    /*
     * Static initializer
     */
    static {
        short counter = 0;
        for (String key: ZoneId.getAvailableZoneIds()) {
            final short index = ++counter;
            final ZoneId zoneId = ZoneId.of(key);
            zoneIdMap1.put(zoneId, index);
            zoneIdMap2.put(index, zoneId);
        }
    }

    private static final long nullValue = Long.MIN_VALUE;
    private static final short NULL_ZONE = -1;
    private static final short UTC_ZONE = zoneIdMap1.get(ZoneId.of("UTC"));

    private int length;
    private MutableIntLongMap values;
    private MutableIntShortMap zoneIds;
    private ZonedDateTime defaultValue;
    private final short defaultZoneId;
    private long defaultValueAsLong;

    /**
     * Constructor
     * @param length    the length for this array
     * @param fillPct   the fill percent for array (0.2 implies 20% filled)
     */
    SparseArrayOfZonedDateTimes(int length, float fillPct, ZonedDateTime defaultValue) {
        super(ZonedDateTime.class, ArrayStyle.SPARSE, false);
        this.length = length;
        this.defaultValue = defaultValue;
        this.defaultValueAsLong = defaultValue != null ? defaultValue.toInstant().toEpochMilli() : nullValue;
        this.defaultZoneId = defaultValue != null ? zoneIdMap1.get(defaultValue.getZone()) : NULL_ZONE;
        this.values = IntLongMaps.mutable.withInitialCapacity((int)Math.max(length * fillPct, 5d));
        this.zoneIds = IntShortMaps.mutable.withInitialCapacity((int)Math.max(length * fillPct, 5d));
    }

    /**
     * Constructor
     * @param source    the source array to shallow copy
     * @param parallel  true for the parallel version
     */
    private SparseArrayOfZonedDateTimes(SparseArrayOfZonedDateTimes source, boolean parallel) {
        super(source.type(), ArrayStyle.SPARSE, parallel);
        this.length = source.length;
        this.defaultValue = source.defaultValue;
        this.defaultValueAsLong = source.defaultValueAsLong;
        this.defaultZoneId = source.defaultZoneId;
        this.values = source.values;
        this.zoneIds = source.zoneIds;
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
    public final ZonedDateTime defaultValue() {
        return defaultValue;
    }

    @Override
    public final Array<ZonedDateTime> parallel() {
        return isParallel() ? this : new SparseArrayOfZonedDateTimes(this, true);
    }


    @Override
    public final Array<ZonedDateTime> sequential() {
        return isParallel() ? new SparseArrayOfZonedDateTimes(this, false) : this;
    }


    @Override()
    public final Array<ZonedDateTime> copy() {
        try {
            final SparseArrayOfZonedDateTimes copy = (SparseArrayOfZonedDateTimes)super.clone();
            copy.values = IntLongMaps.mutable.withAll(values);
            copy.zoneIds = IntShortMaps.mutable.withAll(zoneIds);
            copy.defaultValue = this.defaultValue;
            copy.defaultValueAsLong = this.defaultValueAsLong;
            return copy;
        } catch (Exception ex) {
            throw new ArrayException("Failed to copy Array: " + this, ex);
        }
    }


    @Override()
    public final Array<ZonedDateTime> copy(int[] indexes) {
        var fillPct = (float)values.size() / length();
        var clone = new SparseArrayOfZonedDateTimes(indexes.length, fillPct, defaultValue);
        for (int i = 0; i < indexes.length; ++i) {
            var value = getLong(indexes[i]);
            if (value != defaultValueAsLong) {
                clone.values.put(i, value);
                clone.zoneIds.put(i, this.zoneIds.getIfAbsent(i, defaultZoneId));
            }
        }
        return clone;
    }


    @Override
    public Array<ZonedDateTime> copy(Array<Integer> indexes) {
        var fillPct = (float)values.size() / length();
        var clone = new SparseArrayOfZonedDateTimes(indexes.length(), fillPct, defaultValue);
        for (int i = 0; i < indexes.length(); ++i) {
            var value = getLong(indexes.getInt(i));
            if (value != defaultValueAsLong) {
                clone.values.put(i, value);
                clone.zoneIds.put(i, this.zoneIds.getIfAbsent(i, defaultZoneId));
            }
        }
        return clone;
    }


    @Override()
    public final Array<ZonedDateTime> copy(int start, int end) {
        var length = end - start;
        var fillPct = (float)values.size() / length();
        var clone = new SparseArrayOfZonedDateTimes(length, fillPct, defaultValue);
        for (int i=0; i<length; ++i) {
            var value = getLong(start+i);
            if (value != defaultValueAsLong) {
                var zoneId = zoneIds.getIfAbsent(start+i, defaultZoneId);
                clone.setLong(i, value);
                clone.zoneIds.put(i, zoneId);
            }
        }
        return clone;
    }


    @Override
    protected final Array<ZonedDateTime> sort(int start, int end, int multiplier) {
        return doSort(start, end, (i, j) -> {
            final long v1 = values.getIfAbsent(i, defaultValueAsLong);
            final long v2 = values.getIfAbsent(j, defaultValueAsLong);
            return multiplier * Long.compare(v1, v2);
        });
    }


    @Override
    public final int compare(int i, int j) {
        return Long.compare(
            values.getIfAbsent(i, defaultValueAsLong),
            values.getIfAbsent(j, defaultValueAsLong)
        );
    }


    @Override
    public final Array<ZonedDateTime> swap(int i, int j) {
        final long v1 = values.getIfAbsent(i, defaultValueAsLong);
        final long v2 = values.getIfAbsent(j, defaultValueAsLong);
        final short z1 = zoneIds.getIfAbsent(i, defaultZoneId);
        final short z2 = zoneIds.getIfAbsent(j, defaultZoneId);
        if (v1 == defaultValueAsLong) {
            this.values.remove(j);
            this.zoneIds.remove(j);
        } else {
            this.values.put(j, v1);
            this.zoneIds.put(j, z1);
        }
        if (v2 == defaultValueAsLong) {
            this.values.remove(i);
            this.zoneIds.remove(i);
        } else {
            this.values.put(i, v2);
            this.zoneIds.put(i, z2);
        }
        return this;
    }


    @Override
    public final Array<ZonedDateTime> filter(Predicate<ArrayValue<ZonedDateTime>> predicate) {
        int count = 0;
        var length = this.length();
        final ArrayCursor<ZonedDateTime> cursor = cursor();
        final Array<ZonedDateTime> matches = Array.of(type(), length, loadFactor());
        for (int i=0; i<length; ++i) {
            cursor.moveTo(i);
            final boolean match = predicate.test(cursor);
            if (match) matches.setValue(count++, cursor.getValue());
        }
        return count == length ? matches : matches.copy(0, count);
    }


    @Override
    public final Array<ZonedDateTime> update(Array<ZonedDateTime> from, int[] fromIndexes, int[] toIndexes) {
        if (fromIndexes.length != toIndexes.length) {
            throw new ArrayException("The from index array must have the same length as the to index array");
        } else {
            for (int i=0; i<fromIndexes.length; ++i) {
                final int toIndex = toIndexes[i];
                final int fromIndex = fromIndexes[i];
                final ZonedDateTime update = from.getValue(fromIndex);
                this.setValue(toIndex, update);
            }
        }
        return this;
    }


    @Override
    public final Array<ZonedDateTime> update(int toIndex, Array<ZonedDateTime> from, int fromIndex, int length) {
        if (from instanceof SparseArrayOfZonedDateTimes) {
            final SparseArrayOfZonedDateTimes other = (SparseArrayOfZonedDateTimes)from;
            for (int i=0; i<length; ++i) {
                this.values.put(toIndex + i, other.values.getIfAbsent(fromIndex + i, defaultValueAsLong));
                this.zoneIds.put(toIndex + i, other.zoneIds.getIfAbsent(fromIndex + i, defaultZoneId));
            }
        } else {
            for (int i=0; i<length; ++i) {
                final ZonedDateTime update = from.getValue(fromIndex + i);
                this.setValue(toIndex + i, update);
            }
        }
        return this;
    }


    @Override
    public final Array<ZonedDateTime> expand(int newLength) {
        this.length = newLength > length ? newLength : length;
        return this;
    }


    @Override
    public Array<ZonedDateTime> fill(ZonedDateTime value, int start, int end) {
        final long fillValue = value != null ? value.toInstant().toEpochMilli() : nullValue;
        if (fillValue == defaultValueAsLong) {
            this.values.clear();
            this.zoneIds.clear();
        } else {
            final short fillZoneId = value != null ? zoneIdMap1.get(value.getZone()) : NULL_ZONE;
            for (int i=start; i<end; ++i) {
                this.values.put(i, fillValue);
                this.zoneIds.put(i, fillZoneId);
            }
        }
        return this;
    }


    @Override
    public boolean isNull(int index) {
        return values.getIfAbsent(index, nullValue) == nullValue;
    }


    @Override
    public final boolean isEqualTo(int index, ZonedDateTime value) {
        if (value == null) {
            return isNull(index);
        } else {
            final long epochMills = value.toInstant().toEpochMilli();
            if (epochMills != values.getIfAbsent(index, defaultValueAsLong)) {
                return false;
            } else {
                final ZoneId zoneId = value.getZone();
                final short code1 = zoneIdMap1.get(zoneId);
                final short code2 = zoneIds.getIfAbsent(index, defaultZoneId);
                return code1 == code2;
            }
        }
    }


    @Override
    public final long getLong(int index) {
        this.checkBounds(index, length);
        return values.getIfAbsent(index, defaultValueAsLong);
    }


    @Override
    public final ZonedDateTime getValue(int index) {
        this.checkBounds(index, length);
        final long value = values.getIfAbsent(index, defaultValueAsLong);
        if (value == nullValue) {
            return null;
        } else {
            final ZoneId zone = zoneIdMap2.get(zoneIds.getIfAbsent(index, defaultZoneId));
            final Instant instant = Instant.ofEpochMilli(value);
            return ZonedDateTime.ofInstant(instant, zone);
        }
    }


    @Override
    public final long setLong(int index, long value) {
        this.checkBounds(index, length);
        final long oldValue = getLong(index);
        if (value == defaultValueAsLong) {
            this.values.remove(index);
            this.zoneIds.remove(index);
            return oldValue;
        } else {
            final short zoneId = zoneIds.getIfAbsent(index, defaultZoneId);
            this.values.put(index, value);
            this.zoneIds.put(index, zoneId != NULL_ZONE ? zoneId : UTC_ZONE);
            return oldValue;
        }
    }


    @Override
    public final ZonedDateTime setValue(int index, ZonedDateTime value) {
        this.checkBounds(index, length);
        final ZonedDateTime oldValue = getValue(index);
        final long valueAsLong = value == null ? nullValue : value.toInstant().toEpochMilli();
        if (valueAsLong == defaultValueAsLong) {
            this.values.remove(index);
            this.zoneIds.remove(index);
            return oldValue;
        } else {
            final short zoneId = value == null ? NULL_ZONE : zoneIdMap1.get(value.getZone());
            this.values.put(index, valueAsLong);
            this.zoneIds.put(index, zoneId);
            return oldValue;
        }
    }


    @Override
    public int binarySearch(int start, int end, ZonedDateTime value) {
        int low = start;
        int high = end - 1;
        final long valueAsLong = value.toInstant().toEpochMilli();
        while (low <= high) {
            final int midIndex = (low + high) >>> 1;
            final long midValue = getLong(midIndex);
            final int result = Long.compare(midValue, valueAsLong);
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
    public final void read(ObjectInputStream is, int count) throws IOException {
        for (int i=0; i<count; ++i) {
            final long value = is.readLong();
            this.values.put(i, value);
            if (value != defaultValueAsLong) {
                final short zoneId = is.readShort();
                this.zoneIds.put(i, zoneId);
            }
        }
    }


    @Override
    public final void write(ObjectOutputStream os, int[] indexes) throws IOException {
        for (int index : indexes) {
            final long value = getLong(index);
            os.writeLong(value);
            if (value != defaultValueAsLong) {
                final short zoneId = zoneIds.getIfAbsent(index, defaultZoneId);
                os.writeShort(zoneId);
            }
        }
    }
}
