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
import static lu.luz.jzopfli.Cache.*; import static lu.luz.jzopfli.Hash.*;//#include "lz77.h"
import static lu.luz.jzopfli.Util.*;//#include "util.h"

//#include <assert.h>
import java.nio.*;//#include <stdio.h>
//#include <stdlib.h>
class Lz77 extends Lz77H{
public static void ZopfliInitLZ77Store(ZopfliLZ77Store store) {
  store.size = new int[]{0};
  store.litlens = new int[1][0];
  store.dists = new int[1][0];
}

public static void ZopfliCleanLZ77Store(ZopfliLZ77Store store) {
  store.litlens=null;
  store.dists=null;
}

public static void ZopfliCopyLZ77Store(
    ZopfliLZ77Store source, ZopfliLZ77Store dest) {
  int i;
  ZopfliCleanLZ77Store(dest);
  dest.litlens =
      new int[1][source.size[0]];
  dest.dists = new int[1][source.size[0]];



  dest.size = source.size;
  for (i = 0; i < source.size[0]; i++) {
    dest.litlens[0][i] = source.litlens[0][i];
    dest.dists[0][i] = source.dists[0][i];
  }
}

/**
Appends the length and distance to the LZ77 arrays of the ZopfliLZ77Store.
context must be a ZopfliLZ77Store*.
*/
public static void ZopfliStoreLitLenDist(int length, int dist,
                           ZopfliLZ77Store store) {
  int[] size2 = new int[]{store.size[0]};  /* Needed for using ZOPFLI_APPEND_DATA twice. */
  ZOPFLI_APPEND_DATA(length, store.litlens, store.size);
  ZOPFLI_APPEND_DATA(dist, store.dists, size2);
}

/**
Gets a score of the length given the distance. Typically, the score of the
length is the length itself, but if the distance is very long, decrease the
score of the length a bit to make up for the fact that long distances use large
amounts of extra bits.

This is not an accurate score, it is a heuristic only for the greedy LZ77
implementation. More accurate cost models are employed later. Making this
heuristic more accurate may hurt rather than improve compression.

The two direct uses of this heuristic are:
-avoid using a length of 3 in combination with a long distance. This only has
 an effect if length == 3.
-make a slightly better choice between the two options of the lazy matching.

Indirectly, this affects:
-the block split points if the default of block splitting first is used, in a
 rather unpredictable way
-the first zopfli run, so it affects the chance of the first run being closer
 to the optimal output
*/
private static int GetLengthScore(int length, int distance) {
  /*
  At 1024, the distance uses 9+ extra bits and this seems to be the sweet spot
  on tested files.
  */
  return distance > 1024 ? length - 1 : length;
}

public static void ZopfliVerifyLenDist(byte[] data, int datasize, int pos,
                         int dist, int length) {

  /* TODO(lode): make this only run in a debug compile, it's for assert only. */
  int i;

  assert(pos + length <= datasize);
  for (i = 0; i < length; i++) {
    if (data[pos - dist + i] != data[pos + i]) {
      assert(data[pos - dist + i] == data[pos + i]);
      break;
    }
  }
}

/**
Finds how long the match of scan and match is. Can be used to find how many
bytes starting from scan, and from match, are equal. Returns the last byte
after scan, which is still equal to the corresponding byte after match.
scan is the position to compare
match is the earlier position to compare.
end is the last possible byte, beyond which to stop looking.
safe_end is a few (8) bytes before end, for comparing multiple bytes at once.
*/
private static int GetMatch(byte[] array, int scan,
                                     int match,
                                     int end,
                                     int safe_end) {

//  if (sizeof(size_t) == 8) {
//    /* 8 checks at once per array bounds check (size_t is 64-bit). */
//    while (scan < safe_end && *((size_t*)scan) == *((size_t*)match)) {
//      scan += 8;
//      match += 8;
//    }
//  } else if (sizeof(unsigned int) == 4) {
//    /* 4 checks at once per array bounds check (unsigned int is 32-bit). */
    while (scan < safe_end
    		&& ByteBuffer.wrap(array, scan, 4).getInt() == ByteBuffer.wrap(array, match, 4).getInt()) {
      scan += 4;
      match += 4;
    }
//  } else {
//    /* do 8 checks at once per array bounds check. */
//    while (scan < safe_end && array[scan] == array[++match] && array[++scan] == array[++match]
//          && array[++scan] == array[++match] && array[++scan] == array[++match]
//          && array[++scan] == array[++match] && array[++scan] == array[++match]
//          && array[++scan] == array[++match] && array[++scan] == array[++match]) {
//      scan++; match++;
//      ByteBuffer.wrap(array).g
//    }
//  }

  /* The remaining few bytes. */
  while (scan != end && array[scan] == array[match]) {
    scan++; match++;
  }

  return scan;
}

//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
/**
Gets distance, length and sublen values from the cache if possible.
Returns 1 if it got the values from the cache, 0 if not.
Updates the limit value to a smaller one if possible with more limited
information from the cache.
*/
private static boolean TryGetFromLongestMatchCache(ZopfliBlockState s,
    int pos, int[] limit,
    int[] sublen, int[] distance, int[] length) {
  /* The LMC cache starts at the beginning of the block rather than the
     beginning of the whole array. */
  int lmcpos = pos - s.blockstart;

  /* Length > 0 and dist 0 is invalid combination, which indicates on purpose
     that this cache value is not filled in yet. */
  boolean cache_available = s.lmc!=null && (s.lmc.length[lmcpos] == 0 ||
      s.lmc.dist[lmcpos] != 0);
  boolean limit_ok_for_cache = cache_available &&
      (limit[0] == ZOPFLI_MAX_MATCH || s.lmc.length[lmcpos] <= limit[0] ||
      (sublen!=null && ZopfliMaxCachedSublen(s.lmc,
          lmcpos, s.lmc.length[lmcpos]) >= limit[0]));

  if (s.lmc!=null && limit_ok_for_cache && cache_available) {
    if (sublen==null || s.lmc.length[lmcpos]
        <= ZopfliMaxCachedSublen(s.lmc, lmcpos, s.lmc.length[lmcpos])) {
      length[0] = s.lmc.length[lmcpos];
      if (length[0] > limit[0]) length[0] = limit[0];
      if (sublen!=null) {
        ZopfliCacheToSublen(s.lmc, lmcpos, length[0], sublen);
        distance[0] = sublen[length[0]];
        if (limit[0] == ZOPFLI_MAX_MATCH && length[0] >= ZOPFLI_MIN_MATCH) {
          assert(sublen[length[0]] == s.lmc.dist[lmcpos]);
        }
      } else {
        distance[0] = s.lmc.dist[lmcpos];
      }
      return true;
    }
    /* Can't use much of the cache, since the "sublens" need to be calculated,
       but at  least we already know when to stop. */
    limit[0] = s.lmc.length[lmcpos];
  }

  return false;
}

/**
Stores the found sublen, distance and length in the longest match cache, if
possible.
*/
private static void StoreInLongestMatchCache(ZopfliBlockState s,
    int pos, int limit,
    int[] sublen,
    int distance, int length) {
  /* The LMC cache starts at the beginning of the block rather than the
     beginning of the whole array. */
  int lmcpos = pos - s.blockstart;

  /* Length > 0 and dist 0 is invalid combination, which indicates on purpose
     that this cache value is not filled in yet. */
  boolean cache_available = s.lmc!=null && (s.lmc.length[lmcpos] == 0 ||
      s.lmc.dist[lmcpos] != 0);

  if (s.lmc!=null && limit == ZOPFLI_MAX_MATCH && sublen!=null && !cache_available) {
    assert(s.lmc.length[lmcpos] == 1 && s.lmc.dist[lmcpos] == 0);
    s.lmc.dist[lmcpos] = length < ZOPFLI_MIN_MATCH ? 0 : distance;
    s.lmc.length[lmcpos] = length < ZOPFLI_MIN_MATCH ? 0 : length;
    assert(!(s.lmc.length[lmcpos] == 1 && s.lmc.dist[lmcpos] == 0));
    ZopfliSublenToCache(sublen, lmcpos, length, s.lmc);
  }
}
//#endif

public static void ZopfliFindLongestMatch(ZopfliBlockState s, ZopfliHash h,
    byte[] array,
    int pos, int size, int[] limit,
    int[] sublen, int[] distance, int[] length) {
  int hpos = pos & ZOPFLI_WINDOW_MASK, p, pp;
  int bestdist = 0;
  int bestlength = 1;
  int scan;
  int match;
  int arrayend;
  int arrayend_safe;
//#if ZOPFLI_MAX_CHAIN_HITS < ZOPFLI_WINDOW_SIZE
  int chain_counter = ZOPFLI_MAX_CHAIN_HITS;  /* For quitting early. */
//#endif

  int dist = 0;  /* Not unsigned short on purpose. */

  int[] hhead = h.head;
  int[] hprev = h.prev;
  int[] hhashval = h.hashval;
  int hval = h.val;

//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  if (TryGetFromLongestMatchCache(s, pos, limit, sublen, distance, length)) {
    assert(pos + length[0] <= size);
    return;
  }
//#endif

  assert(limit[0] <= ZOPFLI_MAX_MATCH);
  assert(limit[0] >= ZOPFLI_MIN_MATCH);
  assert(pos < size);

  if (size - pos < ZOPFLI_MIN_MATCH) {
    /* The rest of the code assumes there are at least ZOPFLI_MIN_MATCH bytes to
       try. */
    length[0] = 0;
    distance[0] = 0;
    return;
  }

  if (pos + limit[0] > size) {
    limit[0] = size - pos;
  }
  arrayend = pos + limit[0];
  arrayend_safe = arrayend - 8;

  assert(hval < 65536);

  pp = hhead[hval];  /* During the whole loop, p == hprev[pp]. */
  p = hprev[pp];

  assert(pp == hpos);

  dist = p < pp ? pp - p : ((ZOPFLI_WINDOW_SIZE - p) + pp);

  /* Go through all distances. */
  while (dist < ZOPFLI_WINDOW_SIZE) {
    int currentlength = 0;

    assert(p < ZOPFLI_WINDOW_SIZE);
    assert(p == hprev[pp]);
    assert(hhashval[p] == hval);

    if (dist > 0) {
      assert(pos < size);
      assert(dist <= pos);
      scan = pos;
      match = pos - dist;

      /* Testing the byte at position bestlength first, goes slightly faster. */
      if (pos + bestlength >= size
          || array[scan + bestlength] == array[match + bestlength]) {

//#ifdef ZOPFLI_HASH_SAME
        int same0 = h.same[pos & ZOPFLI_WINDOW_MASK];
        if (same0 > 2 && array[scan] == array[match]) {
          int same1 = h.same[(pos - dist) & ZOPFLI_WINDOW_MASK];
          int same = same0 < same1 ? same0 : same1;
          if (same > limit[0]) same = limit[0];
          scan += same;
          match += same;
        }
//#endif
        scan = GetMatch(array, scan, match, arrayend, arrayend_safe);
        currentlength = scan - pos;  /* The found length. */
      }

      if (currentlength > bestlength) {
        if (sublen!=null) {
          int j;
          for (j = bestlength + 1; j <= currentlength; j++) {
            sublen[j] = dist;
          }
        }
        bestdist = dist;
        bestlength = currentlength;
        if (currentlength >= limit[0]) break;
      }
    }


//#ifdef ZOPFLI_HASH_SAME_HASH
    /* Switch to the other hash once this will be more efficient. */
    if (hhead != h.head2 && bestlength >= h.same[hpos] &&
        h.val2 == h.hashval2[p]) {
      /* Now use the hash that encodes the length and first byte. */
      hhead = h.head2;
      hprev = h.prev2;
      hhashval = h.hashval2;
      hval = h.val2;
    }
//#endif

    pp = p;
    p = hprev[p];
    if (p == pp) break;  /* Uninited prev value. */

    dist += p < pp ? pp - p : ((ZOPFLI_WINDOW_SIZE - p) + pp);

//#if ZOPFLI_MAX_CHAIN_HITS < ZOPFLI_WINDOW_SIZE
    chain_counter--;
    if (chain_counter <= 0) break;
//#endif
  }

//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  StoreInLongestMatchCache(s, pos, limit[0], sublen, bestdist, bestlength);
//#endif

  assert(bestlength <= limit[0]);

  distance[0] = bestdist;
  length[0] = bestlength;
  assert(pos + length[0] <= size);
}

public static void ZopfliLZ77Greedy(ZopfliBlockState s, byte[] in,
                      int instart, int inend,
                      ZopfliLZ77Store store) {
  int i = 0, j;
  int[] leng={0};
  int[] dist={0};
  int lengthscore;
  int windowstart = instart > ZOPFLI_WINDOW_SIZE
      ? instart - ZOPFLI_WINDOW_SIZE : 0;
  int[] dummysublen=new int[259];

  ZopfliHash hash=new ZopfliHash();
  ZopfliHash h = hash;

//#ifdef ZOPFLI_LAZY_MATCHING
  /* Lazy matching. */
  int prev_length = 0;
  int prev_match = 0;
  int prevlengthscore;
  boolean match_available = false;
//#endif

  if (instart == inend) return;

  ZopfliInitHash(ZOPFLI_WINDOW_SIZE, h);
  ZopfliWarmupHash(in, windowstart, inend, h);
  for (i = windowstart; i < instart; i++) {
    ZopfliUpdateHash(in, i, inend, h);
  }

  for (i = instart; i < inend; i++) {
    ZopfliUpdateHash(in, i, inend, h);

    ZopfliFindLongestMatch(s, h, in, i, inend, new int[]{ZOPFLI_MAX_MATCH}, dummysublen,
                           dist, leng);
    lengthscore = GetLengthScore(leng[0], dist[0]);

//#ifdef ZOPFLI_LAZY_MATCHING
    /* Lazy matching. */
    prevlengthscore = GetLengthScore(prev_length, prev_match);
    if (match_available) {
      match_available = false;
      if (lengthscore > prevlengthscore + 1) {
        ZopfliStoreLitLenDist(in[i - 1]&0xFF, 0, store);
        if (lengthscore >= ZOPFLI_MIN_MATCH && leng[0] < ZOPFLI_MAX_MATCH) {
          match_available = true;
          prev_length = leng[0];
          prev_match = dist[0];
          continue;
        }
      } else {
        /* Add previous to output. */
        leng[0] = prev_length;
        dist[0] = prev_match;
        lengthscore = prevlengthscore;
        /* Add to output. */
        ZopfliVerifyLenDist(in, inend, i - 1, dist[0], leng[0]);
        ZopfliStoreLitLenDist(leng[0], dist[0], store);
        for (j = 2; j < leng[0]; j++) {
          assert(i < inend);
          i++;
          ZopfliUpdateHash(in, i, inend, h);
        }
        continue;
      }
    }
    else if (lengthscore >= ZOPFLI_MIN_MATCH && leng[0] < ZOPFLI_MAX_MATCH) {
      match_available = true;
      prev_length = leng[0];
      prev_match = dist[0];
      continue;
    }
    /* End of lazy matching. */
//#endif

    /* Add to output. */
    if (lengthscore >= ZOPFLI_MIN_MATCH) {
      ZopfliVerifyLenDist(in, inend, i, dist[0], leng[0]);
      ZopfliStoreLitLenDist(leng[0], dist[0], store);
    } else {
      leng[0] = 1;
      ZopfliStoreLitLenDist(in[i]&0xFF, 0, store);
    }
    for (j = 1; j < leng[0]; j++) {
      assert(i < inend);
      i++;
      ZopfliUpdateHash(in, i, inend, h);
    }
  }

  ZopfliCleanHash(h);
}

public static void ZopfliLZ77Counts(int[] litlens,
                      int[] dists,
                      int start, int end,
                      int[] ll_count, int[] d_count) {
  int i;

  for (i = 0; i < 288; i++) {
    ll_count[i] = 0;
  }
  for (i = 0; i < 32; i++) {
    d_count[i] = 0;
  }

  for (i = start; i < end; i++) {
    if (dists[i] == 0) {
      ll_count[litlens[i]]++;
    } else {
      ll_count[ZopfliGetLengthSymbol(litlens[i])]++;
      d_count[ZopfliGetDistSymbol(dists[i])]++;
    }
  }

  ll_count[256] = 1;  /* End symbol. */
}
}