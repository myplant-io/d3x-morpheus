/*
 * Copyright (C) 2014-2017 Xavier Witdouck
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

import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.frame.DataFrameAsserts;
import com.d3x.morpheus.frame.DataFrameRow;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests accessing data by rows
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
public class RowAccessTests {


    @DataProvider(name="args3")
    public Object[][] getArgs3() {
        return new Object[][] {
                { boolean.class },
                { int.class },
                { long.class },
                { double.class },
                { Object.class },
        };
    }


    @Test(dataProvider="args3")
    public void testAccessByRowAndIndex(Class type) {
        final DataFrame<String,String> source = TestDataFrames.random(type, 100, 100);
        final DataFrame<String,String> target = source.copy().applyValues(v -> null);
        if (type == boolean.class) {
            source.rows().forEach(row -> {
                final DataFrameRow targetRow = target.row(row.key());
                for (int i = 0; i < source.colCount(); ++i) {
                    targetRow.setBooleanAt(i, row.getBooleanAt(i));
                }
            });
        } else if (type == int.class) {
            source.rows().forEach(row -> {
                final DataFrameRow targetRow = target.row(row.key());
                for (int i = 0; i < source.colCount(); ++i) {
                    targetRow.setIntAt(i, row.getIntAt(i));
                }
            });
        } else if (type == long.class) {
            source.rows().forEach(row -> {
                final DataFrameRow targetRow = target.row(row.key());
                for (int i = 0; i < source.colCount(); ++i) {
                    targetRow.setLongAt(i, row.getLongAt(i));
                }
            });
        } else if (type == double.class) {
            source.rows().forEach(row -> {
                final DataFrameRow targetRow = target.row(row.key());
                for (int i = 0; i < source.colCount(); ++i) {
                    targetRow.setDoubleAt(i, row.getDoubleAt(i));
                }
            });
        } else if (type == Object.class) {
            source.rows().forEach(row -> {
                final DataFrameRow targetRow = target.row(row.key());
                for (int i = 0; i < source.colCount(); ++i) {
                    targetRow.setValueAt(i, row.getValueAt(i));
                }
            });
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        DataFrameAsserts.assertEqualsByIndex(source, target);
    }


    @Test(dataProvider="args3")
    public void testAccessByRowAndKey(Class type) {
        final DataFrame<String,String> source = TestDataFrames.random(type, 100, 100);
        final DataFrame<String,String> target = source.copy().applyValues(v -> null);
        if (type == boolean.class) {
            source.rows().forEach(row -> {
                Assert.assertTrue(row.isRow());
                final DataFrameRow<String,String> targetRow = target.row(row.key());
                source.cols().keys().forEach(key -> targetRow.setBoolean(key, row.getBoolean(key)));
            });
        } else if (type == int.class) {
            source.rows().forEach(row -> {
                Assert.assertTrue(row.isRow());
                final DataFrameRow<String,String> targetRow = target.row(row.key());
                source.cols().keys().forEach(key -> targetRow.setInt(key, row.getInt(key)));
            });
        } else if (type == long.class) {
            source.rows().forEach(row -> {
                Assert.assertTrue(row.isRow());
                final DataFrameRow<String,String> targetRow = target.row(row.key());
                source.cols().keys().forEach(key -> targetRow.setLong(key, row.getLong(key)));
            });
        } else if (type == double.class) {
            source.rows().forEach(row -> {
                Assert.assertTrue(row.isRow());
                final DataFrameRow<String,String> targetRow = target.row(row.key());
                source.cols().keys().forEach(key -> targetRow.setDouble(key, row.getDouble(key)));
            });
        } else if (type == Object.class) {
            source.rows().forEach(row -> {
                Assert.assertTrue(row.isRow());
                final DataFrameRow<String,String> targetRow = target.row(row.key());
                source.cols().keys().forEach(key -> targetRow.setValue(key, row.getValue(key)));
            });
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        DataFrameAsserts.assertEqualsByIndex(source, target);
    }

}
