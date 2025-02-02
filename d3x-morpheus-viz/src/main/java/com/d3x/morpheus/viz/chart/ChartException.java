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
package com.d3x.morpheus.viz.chart;

/**
 * Exception description here...
 *
 * @author Xavier Witdouck
 */
public class ChartException extends RuntimeException {

    /**
     * Constructor
     *
     * @param message the exception message
     */
    public ChartException(String message) {
        this(message, null);
    }

    /**
     * Constructor
     *
     * @param message the exception message
     * @param cause   the root cause, null permitted
     */
    public ChartException(String message, Throwable cause) {
        super(message, cause);
    }
}
