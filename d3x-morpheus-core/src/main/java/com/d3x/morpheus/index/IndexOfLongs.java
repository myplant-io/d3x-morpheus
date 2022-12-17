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
package com.d3x.morpheus.index;

import java.util.function.Predicate;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.array.ArrayBuilder;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;
import org.eclipse.collections.impl.factory.primitive.LongIntMaps;

/**
 * An Index implementation designed to efficiently store long values
 *
 * <p>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></p>
 *
 * @author  Xavier Witdouck
 */
class IndexOfLongs extends IndexBase<Long> {

    private static final long serialVersionUID = 1L;

    private MutableLongIntMap indexMap;

    /**
     * Constructor
     * @param initialSize   the initial size for this index
     */
    IndexOfLongs(int initialSize) {
        super(Array.of(Long.class, initialSize));
        this.indexMap = LongIntMaps.mutable.withInitialCapacity(initialSize);
    }

    /**
     * Constructor
     * @param iterable      the keys for index
     */
    IndexOfLongs(Iterable<Long> iterable) {
        super(iterable);
        this.indexMap = LongIntMaps.mutable.withInitialCapacity(keyArray().length());
        this.keyArray().sequential().forEachValue(v -> {
            final int index = v.index();
            final long key = v.getLong();
            final int size = indexMap.size();
            indexMap.put(key, index);
            if (indexMap.size() <= size) {
                throw new IndexException("Cannot have duplicate keys in index: " + v.getValue());
            }
        });
    }

    /**
     * Constructor
     * @param iterable  the keys for index
     * @param parent    the parent index to initialize from
     */
    private IndexOfLongs(Iterable<Long> iterable, IndexOfLongs parent) {
        super(iterable, parent);
        this.indexMap = LongIntMaps.mutable.withInitialCapacity(keyArray().length());
        this.keyArray().sequential().forEachValue(v -> {
            final long key = v.getLong();
            final int index = parent.indexMap.getIfAbsent(key, -1);
            if (index < 0) throw new IndexException("No match for key: " + v.getValue());
            final int size = indexMap.size();
            indexMap.put(key, index);
            if (indexMap.size() <= size) {
                throw new IndexException("Cannot have duplicate keys in index: " + v.getValue());
            }
        });
    }

    @Override()
    public final Index<Long> filter(Iterable<Long> keys) {
        return new IndexOfLongs(keys, isFilter() ? (IndexOfLongs)parent() : this);
    }

    @Override
    public final Index<Long> filter(Predicate<Long> predicate) {
        final int count = size();
        final ArrayBuilder<Long> builder = ArrayBuilder.of(count / 2, Long.class);
        for (int i=0; i<count; ++i) {
            final long value = keyArray().getLong(i);
            if (predicate.test(value)) {
                builder.appendLong(value);
            }
        }
        final Array<Long> filter = builder.toArray();
        return new IndexOfLongs(filter, isFilter() ? (IndexOfLongs)parent() : this);
    }

    @Override
    public final boolean add(Long key) {
        if (isFilter()) {
            throw new IndexException("Cannot add keys to an filter on another index");
        } else {
            if (indexMap.containsKey(key)) {
                return false;
            } else {
                final int index = indexMap.size();
                this.ensureCapacity(index + 1);
                this.keyArray().setValue(index, key);
                this.indexMap.put(key, index);
                return true;
            }
        }
    }

    @Override
    public final int addAll(Iterable<Long> keys, boolean ignoreDuplicates) {
        if (isFilter()) {
            throw new IndexException("Cannot add keys to an filter on another index");
        } else {
            var count = new int[1];
            keys.forEach(key -> {
                final long keyAsLong = key;
                if (!indexMap.containsKey(keyAsLong)) {
                    final int index = indexMap.size();
                    this.ensureCapacity(index + 1);
                    this.keyArray().setValue(index, keyAsLong);
                    final int size = indexMap.size();
                    indexMap.put(keyAsLong, index);
                    if (!ignoreDuplicates && indexMap.size() < size) {
                        throw new IndexException("Attempt to add duplicate key to index: " + key);
                    }
                    count[0]++;
                }
            });
            return count[0];
        }
    }

    @Override
    public final Index<Long> copy(boolean deep) {
        try {
            final IndexOfLongs clone = (IndexOfLongs)super.copy(deep);
            if (deep) clone.indexMap = LongIntMaps.mutable.withAll(indexMap);
            return clone;
        } catch (Exception ex) {
            throw new IndexException("Failed to clone index", ex);
        }
    }

    @Override
    public int size() {
        return indexMap.size();
    }

    @Override
    public int getCoordinate(Long key) {
        return indexMap.getIfAbsent(key, -1);
    }

    @Override
    public boolean contains(Long key) {
        return indexMap.containsKey(key);
    }

    @Override
    public final int replace(Long existing, Long replacement) {
        final int index = indexMap.removeKeyIfAbsent(existing, -1);
        if (index == -1) {
            throw new IndexException("No match for key: " + existing);
        } else {
            if (indexMap.containsKey(replacement)) {
                throw new IndexException("The replacement key already exists: " + replacement);
            } else {
                final int ordinal = getOrdinalAt(index);
                this.indexMap.put(replacement, index);
                this.keyArray().setValue(ordinal, replacement);
                return index;
            }
        }
    }

    @Override()
    public final void forEachEntry(IndexConsumer<Long> consumer) {
        final int size = size();
        for (int i=0; i<size; ++i) {
            final Long key = keyArray().getValue(i);
            final int index = indexMap.getIfAbsent(key, -1);
            consumer.accept(key, index);
        }
    }

}
