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
package com.d3x.morpheus.frame;

import java.util.stream.Stream;

/**
 * A convenience marker interface used to represent a column vector on a DataFrame
 *
 * The <code>DataFrameVector</code> interface is parameterized in 5 types, which makes a it
 * rather cumbersome to pass around directly. The <code>DataFrameRow</code> and <code>DataFrameColumn</code>
 * interfaces exist to address this, and also provide a strongly typed interface to distinguish
 * row vectors from column vectors.
 *
 * @param <R>   the row key type
 * @param <C>   the column key type
 *
 * <p>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></p>
 *
 * @author  Xavier Witdouck
 */
public interface DataFrameColumn<R,C> extends DataFrameVector<C,R,R,C,DataFrameColumn<R,C>> {

    @Override
    default boolean containsElement(R rowKey) {
        return frame().containsRow(rowKey);
    }

    @Override
    default Stream<R> streamKeys() {
        return frame().rows().keys();
    }

    /**
     * An interface to a movable DataFrameRow
     * @param <R>   the row key type
     * @param <C>   the column key type
     */
    interface Cursor<R,C> extends DataFrameColumn<R,C> {

        /**
         * Adds a column for key if it does not exist, and moves cursor to column key
         * @param colKey    the column key to add
         * @return          this column cursor positioned at column key
         */
        Cursor<R,C> add(C colKey, Class<?> dataType);

        /**
         * Moves the column cursor to the column key specified if it exists
         * @param colKey    the column key
         * @return          true if key exists, false otherwise
         */
        boolean tryKey(C colKey);

        /**
         * Moves the column cursor to the column ordinal if it is not out of bounds
         * @param colOrdinal    the column ordinal
         * @return              true if ordinal is in bounds, false otherwise
         */
        boolean tryOrdinal(int colOrdinal);

        /**
         * Moves the column cursor to the column key specified
         * @param colKey    the column key
         * @return          this column cursor
         */
        Cursor<R,C> atKey(C colKey);

        /**
         * Moves the column cursor to the column ordinal
         * @param colOrdinal    the column ordinal
         * @return              this column cursor
         */
        Cursor<R,C> atOrdinal(int colOrdinal);
    }

}
