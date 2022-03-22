/*
 * Copyright (C) 2014-2021 D3X Systems - All Rights Reserved
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
package com.d3x.morpheus.vector;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

import lombok.NonNull;

/**
 * Provides read-only iteration over a vector view.
 *
 * @author Scott Shaffer
 */
final class VectorIterator implements PrimitiveIterator.OfDouble {
    @NonNull private final D3xVectorView view;
    private int index = 0;

    VectorIterator(@NonNull D3xVectorView view) {
        this.view = view;
    }

    @Override
    public boolean hasNext() {
        return index < view.length();
    }

    @Override
    public double nextDouble() {
        if (hasNext())
            return view.get(index++);
        else
            throw new NoSuchElementException();
    }
}
