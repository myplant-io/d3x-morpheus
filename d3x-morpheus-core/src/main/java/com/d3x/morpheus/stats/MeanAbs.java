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
package com.d3x.morpheus.stats;

/**
 * A Statistic implementation that supports incremental calculation of
 * the mean absolute value in a sample.
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
public class MeanAbs implements Statistic1 {

    private long n;
    private double sum;

    /**
     * Constructor
     */
    public MeanAbs() {
        super();
    }

    @Override
    public long getN() {
        return n;
    }

    @Override
    public double getValue() {
        if (n > 0)
            return sum / n;
        else
            return Double.NaN;
    }

    @Override
    public StatType getType() {
        return StatType.MEAN_ABS;
    }

    @Override
    public long add(double value) {
        if (!Double.isNaN(value)) {
            sum += Math.abs(value);
            ++n;
        }
        return n;
    }

    @Override
    public Statistic1 copy() {
        try {
            return (Statistic1)super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException("Failed to clone statistic", ex);
        }
    }

    @Override()
    public Statistic1 reset() {
        this.n = 0L;
        this.sum = 0d;
        return this;
    }
}
