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
//#include "squeeze.h"

//#include <assert.h>
//#include <math.h>
import java.util.*;//#include <stdio.h>

//#include "blocksplitter.h"
import static lu.luz.jzopfli.Deflate.*; //#include "deflate.h"
import static lu.luz.jzopfli.Tree.*;//#include "tree.h"
import static lu.luz.jzopfli.Hash.*;import lu.luz.jzopfli.Lz77H.*;import static lu.luz.jzopfli.Lz77.*;import static lu.luz.jzopfli.Util.*;//#include "util.h"
class Squeeze extends SqueezeH{
private static class SymbolStats{
  /** The literal and length symbols. */
  int[] litlens = new int[288];
  /** The 32 unique dist symbols, not the 32768 possible dists. */
  int[] dists = new int[32];

  double[] ll_symbols=new double[288];  /* Length of each lit/len symbol in bits. */
  double[] d_symbols=new double[32];  /* Length of each dist symbol in bits. */
} //SymbolStats;

/** Sets everything to 0. */
private static void InitStats(SymbolStats stats) {
  Arrays.fill(stats.litlens, 0);
  Arrays.fill(stats.dists, 0);

  Arrays.fill(stats.ll_symbols, 0);
  Arrays.fill(stats.d_symbols, 0);
}

private static void CopyStats(SymbolStats source, SymbolStats dest) {
  System.arraycopy(source.litlens, 0, dest.litlens, 0, 288);
  System.arraycopy(source.dists, 0, dest.dists, 0, 32);

  System.arraycopy(source.ll_symbols, 0, dest.ll_symbols,
		  0, 288);
  System.arraycopy(source.d_symbols, 0, dest.d_symbols, 0, 32);
}

/** Adds the bit lengths. */
private static void AddWeighedStatFreqs(SymbolStats stats1, double w1,
                                SymbolStats stats2, double w2,
                                SymbolStats result) {
  int i;
  for (i = 0; i < 288; i++) {
    result.litlens[i] =
        (int) (stats1.litlens[i] * w1 + stats2.litlens[i] * w2);
  }
  for (i = 0; i < 32; i++) {
    result.dists[i] =
        (int) (stats1.dists[i] * w1 + stats2.dists[i] * w2);
  }
  result.litlens[256] = 1;  /* End symbol. */
}

private static class RanState {
  int m_w, m_z;
} //RanState;

private static void InitRanState(RanState state) {
  state.m_w = 1;
  state.m_z = 2;
}

/* Get random number: "Multiply-With-Carry" generator of G. Marsaglia */
private static int Ran(RanState state) {
  state.m_z = 36969 * (state.m_z & 65535) + (state.m_z >> 16);
  state.m_w = 18000 * (state.m_w & 65535) + (state.m_w >> 16);
  return ((state.m_z << 16) + state.m_w) & 0x7FFFFFFF;  /* 32-bit result. */
}

private static void RandomizeFreqs(RanState state, int[] freqs, int n) {
  int i;
  for (i = 0; i < n; i++) {
    if ((Ran(state) >> 4) % 3 == 0) freqs[i] = freqs[Ran(state) % n];
  }
}

private static void RandomizeStatFreqs(RanState state, SymbolStats stats) {
  RandomizeFreqs(state, stats.litlens, 288);
  RandomizeFreqs(state, stats.dists, 32);
  stats.litlens[256] = 1;  /* End symbol. */
}

private static void ClearStatFreqs(SymbolStats stats) {
  int i;
  for (i = 0; i < 288; i++) stats.litlens[i] = 0;
  for (i = 0; i < 32; i++) stats.dists[i] = 0;
}

/**
Function that calculates a cost based on a model for the given LZ77 symbol.
litlen: means literal symbol if dist is 0, length otherwise.
*/
private static abstract class CostModelFun{abstract double f(int litlen, int dist, Object context);}

/**
Cost model which should exactly match fixed tree.
type: CostModelFun
*/

private static GetCostFixed GetCostFixed=new GetCostFixed(); private static class GetCostFixed extends CostModelFun{double f(int litlen, int dist, Object unused) {
  //(void)unused;
  if (dist == 0) {
    if (litlen <= 143) return 8;
    else return 9;
  } else {
    int dbits = ZopfliGetDistExtraBits(dist);
    int lbits = ZopfliGetLengthExtraBits(litlen);
    int lsym = ZopfliGetLengthSymbol(litlen);
    double cost = 0;
    if (lsym <= 279) cost += 7;
    else cost += 8;
    cost += 5;  /* Every dist symbol has length 5. */
    return cost + dbits + lbits;
  }
}
}
/**
Cost model based on symbol statistics.
type: CostModelFun
*/
private static GetCostStat GetCostStat=new GetCostStat(); private static class GetCostStat extends CostModelFun{double f(int litlen, int dist, Object context) {
  SymbolStats stats = (SymbolStats)context;
  if (dist == 0) {
    return stats.ll_symbols[litlen];
  } else {
    int lsym = ZopfliGetLengthSymbol(litlen);
    int lbits = ZopfliGetLengthExtraBits(litlen);
    int dsym = ZopfliGetDistSymbol(dist);
    int dbits = ZopfliGetDistExtraBits(dist);
    return stats.ll_symbols[lsym] + lbits + stats.d_symbols[dsym] + dbits;
  }
}}
/**
Finds the minimum possible cost this cost model can return for valid length and
distance symbols.
*/
private static double GetCostModelMinCost(CostModelFun costmodel, Object costcontext) {
  double mincost;
  int bestlength = 0; /* length that has lowest cost in the cost model */
  int bestdist = 0; /* distance that has lowest cost in the cost model */
  int i;
  /*
  Table of distances that have a different distance symbol in the deflate
  specification. Each value is the first distance that has a new symbol. Only
  different symbols affect the cost model so only these need to be checked.
  See RFC 1951 section 3.2.5. Compressed blocks (length and distance codes).
  */





  mincost = ZOPFLI_LARGE_FLOAT;
  for (i = 3; i < 259; i++) {
    double c = costmodel.f(i, 1, costcontext);
    if (c < mincost) {
      bestlength = i;
      mincost = c;
    }
  }

  mincost = ZOPFLI_LARGE_FLOAT;
  for (i = 0; i < 30; i++) {
    double c = costmodel.f(3, dsymbols[i], costcontext);
    if (c < mincost) {
      bestdist = dsymbols[i];
      mincost = c;
    }
  }

  return costmodel.f(bestlength, bestdist, costcontext);
}
private static final int[] dsymbols = new int[]{1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513,769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577};
/**
Performs the forward pass for "squeeze". Gets the most optimal length to reach
every byte from a previous byte, using cost calculations.
s: the ZopfliBlockState
in: the input data array
instart: where to start
inend: where to stop (not inclusive)
costmodel: function to calculate the cost of some lit/len/dist pair.
costcontext: abstract context for the costmodel function
length_array: output array of size (inend - instart) which will receive the best
    length to reach this byte from a previous byte.
returns the cost that was, according to the costmodel, needed to get to the end.
*/
private static double GetBestLengths(ZopfliBlockState s,
                             byte[] in,
                             int instart, int inend,
                             CostModelFun costmodel, Object costcontext,
                             int[] length_array) {
  /* Best cost to get here so far. */
  int blocksize = inend - instart;
  float[] costs;
  int i = 0, k;
  int[] leng={0};
  int[] dist={0};
  int[] sublen=new int[259];
  int windowstart = instart > ZOPFLI_WINDOW_SIZE
      ? instart - ZOPFLI_WINDOW_SIZE : 0;
  ZopfliHash hash=new ZopfliHash();
  ZopfliHash h=hash;
  double result;
  double mincost = GetCostModelMinCost(costmodel, costcontext);

  if (instart == inend) return 0;

  costs = new float[blocksize + 1];


  ZopfliInitHash(ZOPFLI_WINDOW_SIZE, h);
  ZopfliWarmupHash(in, windowstart, inend, h);
  for (i = windowstart; i < instart; i++) {
    ZopfliUpdateHash(in, i, inend, h);
  }

  for (i = 1; i < blocksize + 1; i++) costs[i] = ZOPFLI_LARGE_FLOAT;
  costs[0] = 0;  /* Because it's the start. */
  length_array[0] = 0;

  for (i = instart; i < inend; i++) {
    int j = i - instart;  /* Index in the costs array and length_array. */
    ZopfliUpdateHash(in, i, inend, h);

//#ifdef ZOPFLI_SHORTCUT_LONG_REPETITIONS
    /* If we're in a long repetition of the same character and have more than
    ZOPFLI_MAX_MATCH characters before and after our position. */
    if (h.same[i & ZOPFLI_WINDOW_MASK] > ZOPFLI_MAX_MATCH * 2
        && i > instart + ZOPFLI_MAX_MATCH + 1
        && i + ZOPFLI_MAX_MATCH * 2 + 1 < inend
        && h.same[(i - ZOPFLI_MAX_MATCH) & ZOPFLI_WINDOW_MASK]
            > ZOPFLI_MAX_MATCH) {
      double symbolcost = costmodel.f(ZOPFLI_MAX_MATCH, 1, costcontext);
      /* Set the length to reach each one to ZOPFLI_MAX_MATCH, and the cost to
      the cost corresponding to that length. Doing this, we skip
      ZOPFLI_MAX_MATCH values to avoid calling ZopfliFindLongestMatch. */
      for (k = 0; k < ZOPFLI_MAX_MATCH; k++) {
        costs[j + ZOPFLI_MAX_MATCH] = (float)(costs[j] + symbolcost);
        length_array[j + ZOPFLI_MAX_MATCH] = ZOPFLI_MAX_MATCH;
        i++;
        j++;
        ZopfliUpdateHash(in, i, inend, h);
      }
    }
//#endif

    ZopfliFindLongestMatch(s, h, in, i, inend, new int[]{ZOPFLI_MAX_MATCH}, sublen,
                           dist, leng);

    /* Literal. */
    if (i + 1 <= inend) {
      double newCost = costs[j] + costmodel.f(in[i]&0xFF, 0, costcontext);
      assert(newCost >= 0);
      if (newCost < costs[j + 1]) {
        costs[j + 1] = (float) newCost;
        length_array[j + 1] = 1;
      }
    }
    /* Lengths. */
    for (k = 3; k <= leng[0] && i + k <= inend; k++) {
      double newCost;

      /* Calling the cost model is expensive, avoid this if we are already at
      the minimum possible cost that it can return. */
     if (costs[j + k] - costs[j] <= mincost) continue;

      newCost = costs[j] + costmodel.f(k, sublen[k], costcontext);
      assert(newCost >= 0);
      if (newCost < costs[j + k]) {
        assert(k <= ZOPFLI_MAX_MATCH);
        costs[j + k] = (float) newCost;
        length_array[j + k] = k;
      }
    }
  }

  assert(costs[blocksize] >= 0);
  result = costs[blocksize];

  ZopfliCleanHash(h);
//  free(costs);

  return result;
}

/**
Calculates the optimal path of lz77 lengths to use, from the calculated
length_array. The length_array must contain the optimal length to reach that
byte. The path will be filled with the lengths to use, so its data size will be
the amount of lz77 symbols.
*/
private static void TraceBackwards(int size, int[] length_array,
                           int[][] path, int[] pathsize) {
  int index = size;
  if (size == 0) return;
  for (;;) {
    ZOPFLI_APPEND_DATA(length_array[index], path, pathsize);
    assert(length_array[index] <= index);
    assert(length_array[index] <= ZOPFLI_MAX_MATCH);
    assert(length_array[index] != 0);
    index -= length_array[index];
    if (index == 0) break;
  }

  /* Mirror result. */
  for (index = 0; index < pathsize[0] / 2; index++) {
    int temp = path[0][index];
    path[0][index] = path[0][pathsize[0] - index - 1];
    path[0][pathsize[0] - index - 1] = temp;
  }
}

private static void FollowPath(ZopfliBlockState s,
                       byte[] in, int instart, int inend,
                       int[] path, int pathsize,
                       ZopfliLZ77Store store) {
  int i, j, pos = 0;
  int windowstart = instart > ZOPFLI_WINDOW_SIZE
      ? instart - ZOPFLI_WINDOW_SIZE : 0;

  //int total_length_test = 0;

  ZopfliHash hash=new ZopfliHash();
  ZopfliHash h = hash;

  if (instart == inend) return;

  ZopfliInitHash(ZOPFLI_WINDOW_SIZE, h);
  ZopfliWarmupHash(in, windowstart, inend, h);
  for (i = windowstart; i < instart; i++) {
    ZopfliUpdateHash(in, i, inend, h);
  }

  pos = instart;
  for (i = 0; i < pathsize; i++) {
    int length[] = {path[i]};
    int[] dummy_length={0};
    int[] dist={0};
    assert(pos < inend);

    ZopfliUpdateHash(in, pos, inend, h);

    /* Add to output. */
    if (length[0] >= ZOPFLI_MIN_MATCH) {
      /* Get the distance by recalculating longest match. The found length
      should match the length from the path. */
      ZopfliFindLongestMatch(s, h, in, pos, inend, length, null,
                             dist, dummy_length);
      assert(!(dummy_length[0] != length[0] && length[0] > 2 && dummy_length[0] > 2));
      ZopfliVerifyLenDist(in, inend, pos, dist[0], length[0]);
      ZopfliStoreLitLenDist(length[0], dist[0], store);
      //total_length_test += length[0];
    } else {
      length[0] = 1;
      ZopfliStoreLitLenDist(in[pos]&0xFF, 0, store);
      //total_length_test++;
    }


    assert(pos + length[0] <= inend);
    for (j = 1; j < length[0]; j++) {
      ZopfliUpdateHash(in, pos + j, inend, h);
    }

    pos += length[0];
  }

  ZopfliCleanHash(h);
}

/** Calculates the entropy of the statistics */
private static void CalculateStatistics(SymbolStats stats) {
  ZopfliCalculateEntropy(stats.litlens, 288, stats.ll_symbols);
  ZopfliCalculateEntropy(stats.dists, 32, stats.d_symbols);
}

/** Appends the symbol statistics from the store. */
private static void GetStatistics(ZopfliLZ77Store store, SymbolStats stats) {
  int i;
  for (i = 0; i < store.size[0]; i++) {
    if (store.dists[0][i] == 0) {
      stats.litlens[store.litlens[0][i]]++;
    } else {
      stats.litlens[ZopfliGetLengthSymbol(store.litlens[0][i])]++;
      stats.dists[ZopfliGetDistSymbol(store.dists[0][i])]++;
    }
  }
  stats.litlens[256] = 1;  /* End symbol. */

  CalculateStatistics(stats);
}

/**
Does a single run for ZopfliLZ77Optimal. For good compression, repeated runs
with updated statistics should be performed.

s: the block state
in: the input data array
instart: where to start
inend: where to stop (not inclusive)
path: pointer to dynamically allocated memory to store the path
pathsize: pointer to the size of the dynamic path array
length_array: array if size (inend - instart) used to store lengths
costmodel: function to use as the cost model for this squeeze run
costcontext: abstract context for the costmodel function
store: place to output the LZ77 data
returns the cost that was, according to the costmodel, needed to get to the end.
    This is not the actual cost.
*/
private static double LZ77OptimalRun(ZopfliBlockState s,
    byte[] in, int instart, int inend,
    int[][] path, int[] pathsize,
    int[] length_array, CostModelFun costmodel,
    Object costcontext, ZopfliLZ77Store store) {
  double cost = GetBestLengths(
      s, in, instart, inend, costmodel, costcontext, length_array);
  //free(*path);
  path[0][0] = 0;
  pathsize[0] = 0;
  TraceBackwards(inend - instart, length_array, path, pathsize);
  FollowPath(s, in, instart, inend, path[0], pathsize[0], store);
  assert(cost < ZOPFLI_LARGE_FLOAT);
  return cost;
}

public static void ZopfliLZ77Optimal(ZopfliBlockState s,
                       byte[] in, int instart, int inend,
                       ZopfliLZ77Store store) {
  /* Dist to get to here with smallest cost. */
  int blocksize = inend - instart;
  int[] length_array =
      new int[blocksize + 1];
  int[][] path = {{0}};
  int[] pathsize = {0};
  ZopfliLZ77Store currentstore=new ZopfliLZ77Store();
  SymbolStats stats=new SymbolStats(), beststats=new SymbolStats(), laststats=new SymbolStats();
  int i;
  double cost;
  double bestcost = ZOPFLI_LARGE_FLOAT;
  double lastcost = 0;
  /* Try randomizing the costs a bit once the size stabilizes. */
  RanState ran_state=new RanState();
  int lastrandomstep = -1;



  InitRanState(ran_state);
  InitStats(stats);
  ZopfliInitLZ77Store(currentstore);

  /* Do regular deflate, then loop multiple shortest path runs, each time using
  the statistics of the previous run. */

  /* Initial run. */
  ZopfliLZ77Greedy(s, in, instart, inend, currentstore);
  GetStatistics(currentstore, stats);

  /* Repeat statistics with each time the cost model from the previous stat
  run. */
  for (i = 0; i < s.options.numiterations; i++) {
    ZopfliCleanLZ77Store(currentstore);
    ZopfliInitLZ77Store(currentstore);
    LZ77OptimalRun(s, in, instart, inend, path, pathsize,
                   length_array, GetCostStat, stats,
                   currentstore);
    cost = ZopfliCalculateBlockSize(currentstore.litlens[0], currentstore.dists[0],
                                    0, currentstore.size[0], 2);
    if (s.options.verbose_more || (s.options.verbose && cost < bestcost)) {
      System.err.printf("Iteration %d: %d bit\n", i, (int) cost);
    }
    if (cost < bestcost) {
      /* Copy to the output store. */
      ZopfliCopyLZ77Store(currentstore, store);
      CopyStats(stats, beststats);
      bestcost = cost;
    }
    CopyStats(stats, laststats);
    ClearStatFreqs(stats);
    GetStatistics(currentstore, stats);
    if (lastrandomstep != -1) {
      /* This makes it converge slower but better. Do it only once the
      randomness kicks in so that if the user does few iterations, it gives a
      better result sooner. */
      AddWeighedStatFreqs(stats, 1.0, laststats, 0.5, stats);
      CalculateStatistics(stats);
    }
    if (i > 5 && cost == lastcost) {
      CopyStats(beststats, stats);
      RandomizeStatFreqs(ran_state, stats);
      CalculateStatistics(stats);
      lastrandomstep = i;
    }
    lastcost = cost;
  }

  //free(length_array);
  //free(path);
  ZopfliCleanLZ77Store(currentstore);
}

public static void ZopfliLZ77OptimalFixed(ZopfliBlockState s,
                            byte[] in,
                            int instart, int inend,
                            ZopfliLZ77Store store)
{
  /* Dist to get to here with smallest cost. */
  int blocksize = inend - instart;
  int[] length_array =
      new int[blocksize + 1];
  int[][] path = {{0}};
  int[] pathsize = {0};



  s.blockstart = instart;
  s.blockend = inend;

  /* Shortest path for fixed tree This one should give the shortest possible
  result for fixed tree, no repeated runs are needed since the tree is known. */
  LZ77OptimalRun(s, in, instart, inend, path, pathsize,
                 length_array, GetCostFixed, 0, store);

  //free(length_array);
  //free(path);
}
}