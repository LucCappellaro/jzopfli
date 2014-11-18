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
import static lu.luz.jzopfli.UtilH.*;//#include "cache.h"

//#include <assert.h>
//#include <stdio.h>
//#include <stdlib.h>
class Cache extends CacheH{
//#ifdef ZOPFLI_LONGEST_MATCH_CACHE

public static void ZopfliInitCache(int blocksize, ZopfliLongestMatchCache lmc) {
  int i;
  lmc.length = new int[blocksize];
  lmc.dist = new int[blocksize];
  /* Rather large amount of memory. */
  lmc.sublen = new int[ZOPFLI_CACHE_LENGTH * 3 * blocksize];

  /* length > 0 and dist 0 is invalid combination, which indicates on purpose
  that this cache value is not filled in yet. */
  for (i = 0; i < blocksize; i++) lmc.length[i] = 1;
  for (i = 0; i < blocksize; i++) lmc.dist[i] = 0;
  for (i = 0; i < ZOPFLI_CACHE_LENGTH * blocksize * 3; i++) lmc.sublen[i] = 0;
}

public static void ZopfliCleanCache(ZopfliLongestMatchCache lmc) {
  lmc.length=null;
  lmc.dist=null;
  lmc.sublen=null;
}

public static void ZopfliSublenToCache(int[] sublen,
                         int pos, int length,
                         ZopfliLongestMatchCache lmc) {
  int i;
  int j = 0;
  int bestlength = 0;
  int[] cache;

//#if ZOPFLI_CACHE_LENGTH == 0
//  return;
//#endif

  cache = lmc.sublen; int o=ZOPFLI_CACHE_LENGTH * pos * 3;
  if (length < 3) return;
  for (i = 3; i <= length; i++) {
    if (i == length || sublen[i] != sublen[i + 1]) {
      cache[o + j * 3] = i - 3;
      cache[o + j * 3 + 1] = sublen[i] % 256;
      cache[o + j * 3 + 2] = (sublen[i] >> 8) % 256;
      bestlength = i;
      j++;
      if (j >= ZOPFLI_CACHE_LENGTH) break;
    }
  }
  if (j < ZOPFLI_CACHE_LENGTH) {
    assert(bestlength == length);
    cache[o + (ZOPFLI_CACHE_LENGTH - 1) * 3] = bestlength - 3;
  } else {
    assert(bestlength <= length);
  }
  assert(bestlength == ZopfliMaxCachedSublen(lmc, pos, length));
}

public static void ZopfliCacheToSublen(ZopfliLongestMatchCache lmc,
                         int pos, int length,
                         int[] sublen) {
  int i, j;
  int maxlength = ZopfliMaxCachedSublen(lmc, pos, length);
  int prevlength = 0;
  int[] cache;
//#if ZOPFLI_CACHE_LENGTH == 0
//  return;
//#endif
  if (length < 3) return;
  cache = lmc.sublen; int o=ZOPFLI_CACHE_LENGTH * pos * 3;
  for (j = 0; j < ZOPFLI_CACHE_LENGTH; j++) {
    int llength = cache[o + j * 3] + 3;
    int dist = cache[o + j * 3 + 1] + 256 * cache[o + j * 3 + 2];
    for (i = prevlength; i <= llength; i++) {
      sublen[i] = dist;
    }
    if (llength == maxlength) break;
    prevlength = llength + 1;
  }
}

/**
Returns the length up to which could be stored in the cache.
*/
public static int ZopfliMaxCachedSublen(ZopfliLongestMatchCache lmc,
                               int pos, int length) {
  int[] cache;
//#if ZOPFLI_CACHE_LENGTH == 0
//  return 0;
//#endif
  cache = lmc.sublen; int o=ZOPFLI_CACHE_LENGTH * pos * 3;
//  (void)length;
  if (cache[o+1] == 0 && cache[o+2] == 0) return 0;  /* No sublen cached. */
  return cache[o+(ZOPFLI_CACHE_LENGTH - 1) * 3] + 3;
}

//#endif  /* ZOPFLI_LONGEST_MATCH_CACHE */
}