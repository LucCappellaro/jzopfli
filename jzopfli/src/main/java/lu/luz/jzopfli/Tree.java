/*
Copyright 2011 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Author: lode.vandevenne@gmail.com (Lode Vandevenne)
Author: jyrki.alakuijala@gmail.com (Jyrki Alakuijala)
*/
package lu.luz.jzopfli;
//#include "tree.h"

//#include <assert.h>
import static java.lang.Math.log;//#include <math.h>
//#include <stdio.h>
//#include <stdlib.h>

import static lu.luz.jzopfli.Katajainen.*;//#include "katajainen.h"
//#include "util.h"
class Tree extends TreeH{
public static void ZopfliLengthsToSymbols(int[] lengths, int n, int maxbits,
                            int[] symbols) {
  int[] bl_count = new int[maxbits + 1];
  int[] next_code = new int[maxbits + 1];
  int bits, i;
  int code;

  for (i = 0; i < n; i++) {
    symbols[i] = 0;
  }

  /* 1) Count the number of codes for each code length. Let bl_count[N] be the
  number of codes of length N, N >= 1. */
  for (bits = 0; bits <= maxbits; bits++) {
    bl_count[bits] = 0;
  }
  for (i = 0; i < n; i++) {
    assert(lengths[i] <= maxbits);
    bl_count[lengths[i]]++;
  }
  /* 2) Find the numerical value of the smallest code for each code length. */
  code = 0;
  bl_count[0] = 0;
  for (bits = 1; bits <= maxbits; bits++) {
    code = (code + bl_count[bits-1]) << 1;
    next_code[bits] = code;
  }
  /* 3) Assign numerical values to all codes, using consecutive values for all
  codes of the same length with the base values determined at step 2. */
  for (i = 0;  i < n; i++) {
    int len = lengths[i];
    if (len != 0) {
      symbols[i] = next_code[len];
      next_code[len]++;
    }
  }

  bl_count=null;
  next_code=null;
}

public static void ZopfliCalculateEntropy(int[] count, int n, double[] bitlengths) {
  final double kInvLog2 = 1.4426950408889;  /* 1.0 / log(2.0) */
  int sum = 0;
  int i;
  double log2sum;
  for (i = 0; i < n; ++i) {
    sum += count[i];
  }
  log2sum = (sum == 0 ? log(n) : log(sum)) * kInvLog2;
  for (i = 0; i < n; ++i) {
    /* When the count of the symbol is 0, but its cost is requested anyway, it
    means the symbol will appear at least once anyway, so give it the cost as if
    its count is 1.*/
    if (count[i] == 0) bitlengths[i] = log2sum;
    else bitlengths[i] = log2sum - log(count[i]) * kInvLog2;
    /* Depending on compiler and architecture, the above subtraction of two
    floating point numbers may give a negative result very close to zero
    instead of zero (e.g. -5.973954e-17 with gcc 4.1.2 on Ubuntu 11.4). Clamp
    it to zero. These floating point imprecisions do not affect the cost model
    significantly so this is ok. */
    if (bitlengths[i] < 0 && bitlengths[i] > -1e-5) bitlengths[i] = 0;
    assert(bitlengths[i] >= 0);
  }
}

public static void ZopfliCalculateBitLengths(int[] count, int n, int maxbits,
                               int[] bitlengths) {
  boolean error = ZopfliLengthLimitedCodeLengths(count, n, maxbits, bitlengths);
  //(void) error;
  assert(!error);
}
}