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
//#include "blocksplitter.h"

//#include <assert.h>
//#include <stdio.h>
//#include <stdlib.h>

import static lu.luz.jzopfli.Deflate.*;//#include "deflate.h"
import static lu.luz.jzopfli.ZopfliH.*;import static lu.luz.jzopfli.Lz77H.*;import static lu.luz.jzopfli.Lz77.*;//#include "lz77.h"
//#include "squeeze.h"
//#include "tree.h"
import static lu.luz.jzopfli.UtilH.*;//#include "util.h"
class BlockSplitter extends BlockSplitterH{
/**
The "f" for the FindMinimum function below.
i: the current parameter of f(i)
context: for your implementation
*/
private static abstract class FindMinimumFun{abstract double f(int i, Object context);}

/**
Finds minimum of function f(i) where is is of type size_t, f(i) is of type
double, i is in range start-end (excluding end).
*/
private static int FindMinimum(FindMinimumFun f, Object context,
                          int start, int end) {
  if (end - start < 1024) {
    double best = ZOPFLI_LARGE_FLOAT;
    int result = start;
    int i;
    for (i = start; i < end; i++) {
      double v = f.f(i, context);
      if (v < best) {
        best = v;
        result = i;
      }
    }
    return result;
  } else {
    /* Try to find minimum faster by recursively checking multiple points. */
int NUM=9;  /* Good value: 9. */
    int i;
    int[] p=new int[NUM];
    double[] vp=new double[NUM];
    int besti;
    double best;
    double lastbest = ZOPFLI_LARGE_FLOAT;
    int pos = start;

    for (;;) {
      if (end - start <= NUM) break;

      for (i = 0; i < NUM; i++) {
        p[i] = start + (i + 1) * ((end - start) / (NUM + 1));
        vp[i] = f.f(p[i], context);
      }
      besti = 0;
      best = vp[0];
      for (i = 1; i < NUM; i++) {
        if (vp[i] < best) {
          best = vp[i];
          besti = i;
        }
      }
      if (best > lastbest) break;

      start = besti == 0 ? start : p[besti - 1];
      end = besti == NUM - 1 ? end : p[besti + 1];

      pos = p[besti];
      lastbest = best;
    }
    return pos;
//#undef NUM
  }
}

/**
Returns estimated cost of a block in bits.  It includes the size to encode the
tree and the size to encode all literal, length and distance symbols and their
extra bits.

litlens: lz77 lit/lengths
dists: ll77 distances
lstart: start of block
lend: end of block (not inclusive)
*/
private static double EstimateCost(int[] litlens,
                           int[] dists,
                           int lstart, int lend) {
  return ZopfliCalculateBlockSize(litlens, dists, lstart, lend, 2);
}

private static class SplitCostContext {
  int[] litlens;
  int[] dists;
  int llsize;
  int start;
  int end;
} //SplitCostContext;


/**
Gets the cost which is the sum of the cost of the left and the right section
of the data.
type: FindMinimumFun
*/
private static SplitCost SplitCost=new SplitCost(); private static class SplitCost extends FindMinimumFun{double f(int i, Object context) {
  SplitCostContext c = (SplitCostContext)context;
  return EstimateCost(c.litlens, c.dists, c.start, i) +
      EstimateCost(c.litlens, c.dists, i, c.end);
}}

private static void AddSorted(int value, int[][] out, int[] outsize) {
  int i;
  ZOPFLI_APPEND_DATA(value, out, outsize);
  for (i = 0; i + 1 < outsize[0]; i++) {
    if ((out[0])[i] > value) {
      int j;
      for (j = outsize[0] - 1; j > i; j--) {
        (out[0])[j] = (out[0])[j - 1];
      }
      (out[0])[i] = value;
      break;
    }
  }
}

/**
Prints the block split points as decimal and hex values in the terminal.
*/
private static void PrintBlockSplitPoints(int[] litlens,
                                  int[] dists,
                                  int llsize, int[] lz77splitpoints,
                                  int nlz77points) {
  int[][] splitpoints = {{0}};
  int[] npoints = {0};
  int i;
  /* The input is given as lz77 indices, but we want to see the uncompressed
  index values. */
  int pos = 0;
  if (nlz77points > 0) {
    for (i = 0; i < llsize; i++) {
      int length = dists[i] == 0 ? 1 : litlens[i];
      if (lz77splitpoints[npoints[0]] == i) {
        ZOPFLI_APPEND_DATA(pos, splitpoints, npoints);
        if (npoints[0] == nlz77points) break;
      }
      pos += length;
    }
  }
  assert(npoints[0] == nlz77points);

  System.err.print("block split points: ");
  for (i = 0; i < npoints[0]; i++) {
	  System.err.printf("%d ", (int)splitpoints[0][i]);
  }
  System.err.print("(hex:");
  for (i = 0; i < npoints[0]; i++) {
	  System.err.printf(" %x", (int)splitpoints[0][i]);
  }
  System.err.printf(")\n");

  splitpoints=null;
}

/**
Finds next block to try to split, the largest of the available ones.
The largest is chosen to make sure that if only a limited amount of blocks is
requested, their sizes are spread evenly.
llsize: the size of the LL77 data, which is the size of the done array here.
done: array indicating which blocks starting at that position are no longer
    splittable (splitting them increases rather than decreases cost).
splitpoints: the splitpoints found so far.
npoints: the amount of splitpoints found so far.
lstart: output variable, giving start of block.
lend: output variable, giving end of block.
returns 1 if a block was found, 0 if no block found (all are done).
*/
private static boolean FindLargestSplittableBlock(
    int llsize, boolean[] done,
    int[] splitpoints, int npoints,
    int[] lstart, int[] lend) {
  int longest = 0;
  boolean found = false;
  int i;
  for (i = 0; i <= npoints; i++) {
    int start = i == 0 ? 0 : splitpoints[i - 1];
    int end = i == npoints ? llsize - 1 : splitpoints[i];
    if (!done[start] && end - start > longest) {
      lstart[0] = start;
      lend[0] = end;
      found = true;
      longest = end - start;
    }
  }
  return found;
}

public static void ZopfliBlockSplitLZ77(ZopfliOptions options,
                          int[] litlens,
                          int[] dists,
                          int llsize, int maxblocks,
                          int[][] splitpoints, int[] npoints) {
  int[] lstart={0}, lend={0};
  int i;
  int llpos = 0;
  int numblocks = 1;
  boolean[] done;
  double splitcost, origcost;

  if (llsize < 10) return;  /* This code fails on tiny files. */

  done = new boolean[llsize];

  for (i = 0; i < llsize; i++) done[i] = false;

  lstart[0] = 0;
  lend[0] = llsize;
  for (;;) {
    SplitCostContext c = new SplitCostContext();

    if (maxblocks > 0 && numblocks >= maxblocks) {
      break;
    }

    c.litlens = litlens;
    c.dists = dists;
    c.llsize = llsize;
    c.start = lstart[0];
    c.end = lend[0];
    assert(lstart[0] < lend[0]);
    llpos = FindMinimum(SplitCost, c, lstart[0] + 1, lend[0]);

    assert(llpos > lstart[0]);
    assert(llpos < lend[0]);

    splitcost = EstimateCost(litlens, dists, lstart[0], llpos) +
        EstimateCost(litlens, dists, llpos, lend[0]);
    origcost = EstimateCost(litlens, dists, lstart[0], lend[0]);

    if (splitcost > origcost || llpos == lstart[0] + 1 || llpos == lend[0]) {
      done[lstart[0]] = true;
    } else {
      AddSorted(llpos, splitpoints, npoints);
      numblocks++;
    }

    if (!FindLargestSplittableBlock(
        llsize, done, splitpoints[0], npoints[0], lstart, lend)) {
      break;  /* No further split will probably reduce compression. */
    }

    if (lend[0] - lstart[0] < 10) {
      break;
    }
  }

  if (options.verbose) {
    PrintBlockSplitPoints(litlens, dists, llsize, splitpoints[0], npoints[0]);
  }

  done=null;
}

public static void ZopfliBlockSplit(ZopfliOptions options,
                      byte[] in, int instart, int inend,
                      int maxblocks, int[][] splitpoints, int[] npoints) {
  int pos = 0;
  int i;
  ZopfliBlockState s=new ZopfliBlockState();
  int[][] lz77splitpoints = {{0}};
  int[] nlz77points = {0};
  ZopfliLZ77Store store=new ZopfliLZ77Store();

  ZopfliInitLZ77Store(store);

  s.options = options;
  s.blockstart = instart;
  s.blockend = inend;
//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  s.lmc = null;
//#endif

  npoints[0] = 0;
  splitpoints[0] = new int[0];

  /* Unintuitively, Using a simple LZ77 method here instead of ZopfliLZ77Optimal
  results in better blocks. */
  ZopfliLZ77Greedy(s, in, instart, inend, store);

  ZopfliBlockSplitLZ77(options,
                       store.litlens[0], store.dists[0], store.size[0], maxblocks,
                       lz77splitpoints, nlz77points);

  /* Convert LZ77 positions to positions in the uncompressed input. */
  pos = instart;
  if (nlz77points[0] > 0) {
    for (i = 0; i < store.size[0]; i++) {
      int length = store.dists[0][i] == 0 ? 1 : store.litlens[0][i];
      if (lz77splitpoints[0][npoints[0]] == i) {
        ZOPFLI_APPEND_DATA(pos, splitpoints, npoints);
        if (npoints[0] == nlz77points[0]) break;
      }
      pos += length;
    }
  }
  assert(npoints[0] == nlz77points[0]);

  lz77splitpoints=null;
  ZopfliCleanLZ77Store(store);
}

public static void ZopfliBlockSplitSimple(byte[] in,
                            int instart, int inend,
                            int blocksize,
                            int[][] splitpoints, int[] npoints) {
  int i = instart;
  while (i < inend) {
    ZOPFLI_APPEND_DATA(i, splitpoints, npoints);
    i += blocksize;
  }
  //(void)in;
}
}