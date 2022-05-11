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
package com.d3x.morpheus.linalg;

import com.d3x.morpheus.matrix.D3xMatrix;
import com.d3x.morpheus.stats.Max;
import com.d3x.morpheus.util.DoubleComparator;
import com.d3x.morpheus.vector.D3xVector;

/**
 * Performs a singular value decomposition on a matrix.
 *
 * <p>The singular value decomposition of an {@code M x N} matrix {@code A}
 * is defined as {@code A = U'DV}, where {@code U} is an {@code M x N}
 * column-orthogonal matrix, {@code D} is a diagonal {@code N x N} matrix
 * of the non-negative singular values, and {@code V} is an {@code N x N}
 * orthogonal matrix.</p>
 */
public interface SVD {
    /**
     * Returns the original {@code M x N} matrix.
     * @return the original {@code M x N} matrix.
     */
    D3xMatrix getA();

    /**
     * Returns the {@code M x N} column-orthogonal factor {@code U}.
     * @return the {@code M x N} column-orthogonal factor {@code U}.
     */
    D3xMatrix getU();

    /**
     * Returns the {@code N x M} transpose of the column-orthogonal factor {@code U}.
     * @return the {@code N x M} transpose of the column-orthogonal factor {@code U}.
     */
    default D3xMatrix getUT() {
        return getU().transpose();
    }

    /**
     * Returns the {@code N x N} diagonal matrix of non-negative singular values.
     * @return the {@code N x N} diagonal matrix of non-negative singular values.
     */
    D3xMatrix getD();

    /**
     * Returns the vector of non-negative singular values.
     * @return the vector of non-negative singular values.
     */
    default D3xVector getSingularValueVector() {
        return getD().getDiagonal();
    }

    /**
     * Returns the {@code N x N} orthogonal factor {@code V}.
     * @return the {@code N x N} orthogonal factor {@code V}.
     */
    D3xMatrix getV();

    /**
     * Returns the transpose of the {@code N x N} orthogonal factor {@code V}.
     * @return the transpose of the {@code N x N} orthogonal factor {@code V}.
     */
    default D3xMatrix getVT() {
        return getV().transpose();
    }

    /**
     * Returns the row dimension of the original matrix.
     * @return the row dimension of the original matrix.
     */
    default int getRowDimension() {
        return getA().nrow();
    }

    /**
     * Returns the column dimension of the original matrix.
     * @return the column dimension of the original matrix.
     */
    default int getColumnDimension() {
        return getA().ncol();
    }

    /**
     * Returns the rank of the original matrix.
     * @return the rank of the original matrix.
     */
    default int getRank() {
        int rank = 0;
        double thresh = getSingularValueThreshold();
        D3xVector values = getSingularValueVector();

        for (int index = 0; index < values.length(); ++index)
            if (values.get(index) > thresh)
                ++rank;

        return rank;
    }

    /**
     * Returns the default singular value threshold for this decomposition: the
     * singular values below this threshold are within the estimated numerical
     * precision of zero and should be treated as if they are exactly zero when
     * they are used to solve linear systems or determine the rank of a matrix.
     *
     * <p>We use the expression implemented in the {@code SVD::solve} method from
     * Section 2.6 of <em>Numerical Recipes, 3rd Edition</em>, which is a function
     * of the dimensions of the linear system, the maximum singular value, and the
     * machine tolerance.
     *
     * @return the default singular value threshold for this decomposition.
     */
    default double getSingularValueThreshold() {
        //
        // See the SVD::solve method in Section 2.6 of Numerical Recipes, 3rd Edition...
        //
        int M = getRowDimension();
        int N = getColumnDimension();
        double eps = DoubleComparator.epsilon();
        double wmax = new Max().compute(getSingularValueVector());

        return Math.max(2.0 * eps, 0.5 * Math.sqrt(M + N + 1.0) * wmax * eps);
    }

    /**
     * Creates a new decomposition using the Apache Commons Math library.
     *
     * @param A the matrix to decompose.
     *
     * @return a new singular value decomposition of the specified matrix.
     */
    static SVD apache(D3xMatrix A) {
        return ApacheSVD.create(A);
    }
}
