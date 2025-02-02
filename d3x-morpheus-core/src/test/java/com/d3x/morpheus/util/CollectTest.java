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
package com.d3x.morpheus.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CollectTest {
    @Test
    public void testCollect() {
        Collection<String> actual = Collect.collect(new LinkedHashSet<>(), List.of("A", "B", "A"));
        Collection<String> expected = new LinkedHashSet<>(List.of("A", "B"));

        assertEquals(actual, expected);

        Collect.collect(actual, List.of("A", "B", "C", "D").stream());
        expected.addAll(List.of("C", "D"));

        assertEquals(actual, expected);
    }
}
