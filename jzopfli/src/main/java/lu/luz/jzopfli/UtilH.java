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
/*
Several utilities, including: #defines to try different compression results,
basic deflate specification values and generic program options.
*/

//#ifndef ZOPFLI_UTIL_H_
//#define ZOPFLI_UTIL_H_

//#include <string.h>
import java.util.*;//#include <stdlib.h>
public abstract class UtilH{
/* Minimum and maximum length that can be encoded in deflate. */
public static final int ZOPFLI_MAX_MATCH=258;
public static final int ZOPFLI_MIN_MATCH=3;

/**
The window size for deflate. Must be a power of two. This should be 32768, the
maximum possible by the deflate spec. Anything less hurts compression more than
speed.
*/
public static final int ZOPFLI_WINDOW_SIZE=32768;

/**
The window mask used to wrap indices into the window. This is why the
window size must be a power of two.
*/
public static final int ZOPFLI_WINDOW_MASK=(ZOPFLI_WINDOW_SIZE - 1);

/**
A block structure of huge, non-smart, blocks to divide the input into, to allow
operating on huge files without exceeding memory, such as the 1GB wiki9 corpus.
The whole compression algorithm, including the smarter block splitting, will
be executed independently on each huge block.
Dividing into huge blocks hurts compression, but not much relative to the size.
Set this to, for example, 20MB (20000000). Set it to 0 to disable master blocks.
*/
public static final int ZOPFLI_MASTER_BLOCK_SIZE=20000000;

/**
Used to initialize costs for example
*/
public static final float ZOPFLI_LARGE_FLOAT=1e30f;

/**
For longest match cache. max 256. Uses huge amounts of memory but makes it
faster. Uses this many times three bytes per single byte of the input data.
This is so because longest match finding has to find the exact distance
that belongs to each length for the best lz77 strategy.
Good values: e.g. 5, 8.
*/
public static final int ZOPFLI_CACHE_LENGTH=8;

/**
limit the max hash chain hits for this hash value. This has an effect only
on files where the hash value is the same very often. On these files, this
gives worse compression (the value should ideally be 32768, which is the
ZOPFLI_WINDOW_SIZE, while zlib uses 4096 even for best level), but makes it
faster on some specific files.
Good value: e.g. 8192.
*/
public static final int ZOPFLI_MAX_CHAIN_HITS=8192;

/**
Whether to use the longest match cache for ZopfliFindLongestMatch. This cache
consumes a lot of memory but speeds it up. No effect on compression size.
*/
public static final boolean ZOPFLI_LONGEST_MATCH_CACHE=true;

/**
Enable to remember amount of successive identical bytes in the hash chain for
finding longest match
required for ZOPFLI_HASH_SAME_HASH and ZOPFLI_SHORTCUT_LONG_REPETITIONS
This has no effect on the compression result, and enabling it increases speed.
*/
public static final boolean ZOPFLI_HASH_SAME=true;

/**
Switch to a faster hash based on the info from ZOPFLI_HASH_SAME once the
best length so far is long enough. This is way faster for files with lots of
identical bytes, on which the compressor is otherwise too slow. Regular files
are unaffected or maybe a tiny bit slower.
This has no effect on the compression result, only on speed.
*/
public static final boolean ZOPFLI_HASH_SAME_HASH=true;

/**
Enable this, to avoid slowness for files which are a repetition of the same
character more than a multiple of ZOPFLI_MAX_MATCH times. This should not affect
the compression result.
*/
public static final boolean ZOPFLI_SHORTCUT_LONG_REPETITIONS=true;

/**
Whether to use lazy matching in the greedy LZ77 implementation. This gives a
better result of ZopfliLZ77Greedy, but the effect this has on the optimal LZ77
varies from file to file.
*/
public static final boolean ZOPFLI_LAZY_MATCHING=true;

/*
Gets the symbol for the given length, cfr. the DEFLATE spec.
Returns the symbol in the range [257-285] (inclusive)
*/
//abstract int ZopfliGetLengthSymbol(int l);

/* Gets the amount of extra bits for the given length, cfr. the DEFLATE spec. */
//abstract int ZopfliGetLengthExtraBits(int l);

/* Gets value of the extra bits for the given length, cfr. the DEFLATE spec. */
//abstract int ZopfliGetLengthExtraBitsValue(int l);

/* Gets the symbol for the given dist, cfr. the DEFLATE spec. */
//abstract int ZopfliGetDistSymbol(int dist);

/* Gets the amount of extra bits for the given dist, cfr. the DEFLATE spec. */
//abstract int ZopfliGetDistExtraBits(int dist);

/* Gets value of the extra bits for the given dist, cfr. the DEFLATE spec. */
//abstract int ZopfliGetDistExtraBitsValue(int dist);

/**
Appends value to dynamically allocated memory, doubling its allocation size
whenever needed.

value: the value to append, type T
data: pointer to the dynamic array to append to, type T**
size: pointer to the size of the array to append to, type size_t*. This is the
size that you consider the array to be, not the internal allocation size.
Precondition: allocated size of data is at least a power of two greater than or
equal than *size.
*/
//#ifdef __cplusplus /* C++ cannot assign void* from malloc to *data */
public static void ZOPFLI_APPEND_DATA(int value, byte[][] data, int[] size) {
  if (((size[0]) & ((size[0]) - 1))==0) {
    /*double alloc size if it's a power of two*/
	byte[][] data_void = data;
    data_void[0] = (size[0]) == 0 ? new byte[1]
                              : Arrays.copyOf(data[0], size[0]*2);
  }
  data[0][size[0]]= (byte)value;
  size[0]++;
}
//#else /* C gives problems with strict-aliasing rules for (void**) cast */
public static void ZOPFLI_APPEND_DATA(int value, int[][] data, int[] size) {
  if (((size[0]) & ((size[0]) - 1))==0) {
    /*double alloc size if it's a power of two*/
    data[0] = (size[0]) == 0 ? new int[1]
                           : Arrays.copyOf(data[0], size[0]*2);
  }
  (data[0])[(size[0])] = (value);
  (size[0])++;
}
//#endif


//#endif  /* ZOPFLI_UTIL_H_ */
}