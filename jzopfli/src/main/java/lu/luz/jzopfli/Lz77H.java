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
/**
Functions for basic LZ77 compression and utilities for the "squeeze" LZ77
compression.
*/

//#ifndef ZOPFLI_LZ77_H_
//#define ZOPFLI_LZ77_H_

//#include <stdlib.h>

import lu.luz.jzopfli.CacheH.*;//#include "cache.h"
//#include "hash.h"
import lu.luz.jzopfli.ZopfliH.*;//#include "zopfli.h"
abstract class Lz77H{
/**
Stores lit/length and dist pairs for LZ77.
Parameter litlens: Contains the literal symbols or length values.
Parameter dists: Contains the distances. A value is 0 to indicate that there is
no dist and the corresponding litlens value is a literal instead of a length.
Parameter size: The size of both the litlens and dists arrays.
The memory can best be managed by using ZopfliInitLZ77Store to initialize it,
ZopfliCleanLZ77Store to destroy it, and ZopfliStoreLitLenDist to append values.

*/
public static class ZopfliLZ77Store {
  int[][] litlens;  /* Lit or len. */
  int[][] dists;  /* If 0: indicates literal in corresponding litlens,
      if > 0: length in corresponding litlens, this is the distance. */
  int[] size;
} //ZopfliLZ77Store;

//void ZopfliInitLZ77Store(ZopfliLZ77Store store);
//void ZopfliCleanLZ77Store(ZopfliLZ77Store store);
//void ZopfliCopyLZ77Store(ZopfliLZ77Store source, ZopfliLZ77Store dest);
//void ZopfliStoreLitLenDist(int length, int dist,
//                           ZopfliLZ77Store store);

/**
Some state information for compressing a block.
This is currently a bit under-used (with mainly only the longest match cache),
but is kept for easy future expansion.
*/
public static class ZopfliBlockState {
  ZopfliOptions options;

//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  /* Cache for length/distance pairs found so far. */
  ZopfliLongestMatchCache lmc;
//#endif

  /* The start (inclusive) and end (not inclusive) of the current block. */
  int blockstart;
  int blockend;
} //ZopfliBlockState;

/*
Finds the longest match (length and corresponding distance) for LZ77
compression.
Even when not using "sublen", it can be more efficient to provide an array,
because only then the caching is used.
array: the data
pos: position in the data to find the match for
size: size of the data
limit: limit length to maximum this value (default should be 258). This allows
    finding a shorter dist for that length (= less extra bits). Must be
    in the range [ZOPFLI_MIN_MATCH, ZOPFLI_MAX_MATCH].
sublen: output array of 259 elements, or null. Has, for each length, the
    smallest distance required to reach this length. Only 256 of its 259 values
    are used, the first 3 are ignored (the shortest length is 3. It is purely
    for convenience that the array is made 3 longer).
*/
//void ZopfliFindLongestMatch(
//    ZopfliBlockState s, ZopfliHash h, byte[] array,
//    int pos, int size, int limit,
//    int[] sublen, int[] distance, int[] length);

/*
Verifies if length and dist are indeed valid, only used for assertion.
*/
//void ZopfliVerifyLenDist(byte[] data, int datasize, int pos,
//                         int dist, int length);

/*
Counts the number of literal, length and distance symbols in the given lz77
arrays.
litlens: lz77 lit/lengths
dists: ll77 distances
start: where to begin counting in litlens and dists
end: where to stop counting in litlens and dists (not inclusive)
ll_count: count of each lit/len symbol, must have size 288 (see deflate
    standard)
d_count: count of each dist symbol, must have size 32 (see deflate standard)
*/
//void ZopfliLZ77Counts(int[] litlens,
//                      int[] dists,
//                      int start, int end,
//                      int[] ll_count, int[] d_count);

/*
Does LZ77 using an algorithm similar to gzip, with lazy matching, rather than
with the slow but better "squeeze" implementation.
The result is placed in the ZopfliLZ77Store.
If instart is larger than 0, it uses values before instart as starting
dictionary.
*/
//void ZopfliLZ77Greedy(ZopfliBlockState s, byte[] in,
//                      int instart, int inend,
//                      ZopfliLZ77Store store);

//#endif  /* ZOPFLI_LZ77_H_ */
}