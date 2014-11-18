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
import static lu.luz.jzopfli.Util.*;//#include "deflate.h"

//#include <assert.h>
//#include <stdio.h>
//#include <stdlib.h>
import static lu.luz.jzopfli.ZopfliH.*;
import static lu.luz.jzopfli.BlockSplitter.*;//#include "blocksplitter.h"
import static lu.luz.jzopfli.Lz77.*;import lu.luz.jzopfli.CacheH.*;import static lu.luz.jzopfli.Cache.*;//#include "lz77.h"
import static lu.luz.jzopfli.Squeeze.*;//#include "squeeze.h"
import static lu.luz.jzopfli.Tree.*;//#include "tree.h"
public class Deflate extends DeflateH{
/**
bp = bitpointer, always in range [0, 7].
The outsize is number of necessary bytes to encode the bits.
Given the value of bp and the amount of bytes, the amount of bits represented
is not simply bytesize * 8 + bp because even representing one bit requires a
whole byte. It is: (bp == 0) ? (bytesize * 8) : ((bytesize - 1) * 8 + bp)
*/
private static void AddBit(boolean bit,
                   byte[] bp, byte[][] out, int[] outsize) {
  if (bp[0] == 0) ZOPFLI_APPEND_DATA(0, out, outsize);
  out[0][outsize[0] - 1] |= (bit?1:0) << bp[0];
  bp[0] = (byte)((bp[0] + 1) & 7);
}

private static void AddBits(int symbol, int length,
                    byte[] bp, byte[][] out, int[] outsize) {
  /* TODO(lode): make more efficient (add more bits at once). */
  int i;
  for (i = 0; i < length; i++) {
    int bit = (symbol >> i) & 1;
    if (bp[0]  == 0) ZOPFLI_APPEND_DATA(0, out, outsize);
    (out[0])[outsize[0] - 1] |= bit << bp[0];
    bp[0] = (byte)((bp[0] + 1) & 7);
  }
}

/**
Adds bits, like AddBits, but the order is inverted. The deflate specification
uses both orders in one standard.
*/
private static void AddHuffmanBits(int symbol, int length,
                           byte[] bp, byte[][] out,
                           int[] outsize) {
  /* TODO(lode): make more efficient (add more bits at once). */
  int i;
  for (i = 0; i < length; i++) {
    int bit = (symbol >> (length - i - 1)) & 1;
    if (bp[0] == 0) ZOPFLI_APPEND_DATA(0, out, outsize);
    (out[0])[outsize[0] - 1] |= bit << bp[0];
    bp[0] = (byte)((bp[0] + 1) & 7);
  }
}

/**
Ensures there are at least 2 distance codes to support buggy decoders.
Zlib 1.2.1 and below have a bug where it fails if there isn't at least 1
distance code (with length > 0), even though it's valid according to the
deflate spec to have 0 distance codes. On top of that, some mobile phones
require at least two distance codes. To support these decoders too (but
potentially at the cost of a few bytes), add dummy code lengths of 1.
References to this bug can be found in the changelog of
Zlib 1.2.2 and here: http://www.jonof.id.au/forum/index.php?topic=515.0.

d_lengths: the 32 lengths of the distance codes.
*/
private static void PatchDistanceCodesForBuggyDecoders(int[] d_lengths) {
  int num_dist_codes = 0; /* Amount of non-zero distance codes */
  int i;
  for (i = 0; i < 30 /* Ignore the two unused codes from the spec */; i++) {
    if (d_lengths[i]!=0) num_dist_codes++;
    if (num_dist_codes >= 2) return; /* Two or more codes is fine. */
  }

  if (num_dist_codes == 0) {
    d_lengths[0] = d_lengths[1] = 1;
  } else if (num_dist_codes == 1) {
    d_lengths[d_lengths[0]!=0 ? 1 : 0] = 1;
  }
}
private static final int[] order = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
/**
Encodes the Huffman tree and returns how many bits its encoding takes. If out
is a null pointer, only returns the size and runs faster.
*/
private static int EncodeTree(int[] ll_lengths,
                           int[] d_lengths,
                           boolean use_16, boolean use_17, boolean use_18,
                           byte[] bp,
                           byte[][] out, int[] outsize) {
  int lld_total;  /* Total amount of literal, length, distance codes. */
  /* Runlength encoded version of lengths of litlen and dist trees. */
  int[][] rle = {{0}};
  int[][] rle_bits = {{0}};  /* Extra bits for rle values 16, 17 and 18. */
  int[] rle_size = {0};  /* Size of rle array. */
  int[] rle_bits_size = {0};  /* Should have same value as rle_size. */
  int hlit = 29;  /* 286 - 257 */
  int hdist = 29;  /* 32 - 1, but gzip does not like hdist > 29.*/
  int hclen;
  int hlit2;
  int i, j;
  int[] clcounts=new int[19];
  int[] clcl=new int[19];  /* Code length code lengths. */
  int[] clsymbols=new int[19];
  /* The order in which code length code lengths are encoded as per deflate. */


  
  boolean size_only = out==null;
  int result_size = 0;

  for(i = 0; i < 19; i++) clcounts[i] = 0;

  /* Trim zeros. */
  while (hlit > 0 && ll_lengths[257 + hlit - 1] == 0) hlit--;
  while (hdist > 0 && d_lengths[1 + hdist - 1] == 0) hdist--;
  hlit2 = hlit + 257;
  
  lld_total = hlit2 + hdist + 1;

  for (i = 0; i < lld_total; i++) {
    /* This is an encoding of a huffman tree, so now the length is a symbol */
    int symbol = i < hlit2 ? ll_lengths[i] : d_lengths[i - hlit2];
    int count = 1;
    if(use_16 || (symbol == 0 && (use_17 || use_18))) {
      for (j = i + 1; j < lld_total && symbol ==
          (j < hlit2 ? ll_lengths[j] : d_lengths[j - hlit2]); j++) {
        count++;
      }
    }
    i += count - 1;

    /* Repetitions of zeroes */
    if (symbol == 0 && count >= 3) {
      if (use_18) {
        while (count >= 11) {
          int count2 = count > 138 ? 138 : count;
          if (!size_only) {
            ZOPFLI_APPEND_DATA(18, rle, rle_size);
            ZOPFLI_APPEND_DATA(count2 - 11, rle_bits, rle_bits_size);
          }
          clcounts[18]++;
          count -= count2;
        }
      }
      if (use_17) {
        while (count >= 3) {
          int count2 = count > 10 ? 10 : count;
          if (!size_only) {
            ZOPFLI_APPEND_DATA(17, rle, rle_size);
            ZOPFLI_APPEND_DATA(count2 - 3, rle_bits, rle_bits_size);
          }
          clcounts[17]++;
          count -= count2;
        }
      }
    }

    /* Repetitions of any symbol */
    if (use_16 && count >= 4) {
      count--;  /* Since the first one is hardcoded. */
      clcounts[symbol]++;
      if (!size_only) {
        ZOPFLI_APPEND_DATA(symbol, rle, rle_size);
        ZOPFLI_APPEND_DATA(0, rle_bits, rle_bits_size);
      }
      while (count >= 3) {
        int count2 = count > 6 ? 6 : count;
        if (!size_only) {
          ZOPFLI_APPEND_DATA(16, rle, rle_size);
          ZOPFLI_APPEND_DATA(count2 - 3, rle_bits, rle_bits_size);
        }
        clcounts[16]++;
        count -= count2;
      }
    }

    /* No or insufficient repetition */
    clcounts[symbol] += count;
    while (count > 0) {
      if (!size_only) {
        ZOPFLI_APPEND_DATA(symbol, rle, rle_size);
        ZOPFLI_APPEND_DATA(0, rle_bits, rle_bits_size);
      }
      count--;
    }
  }

  ZopfliCalculateBitLengths(clcounts, 19, 7, clcl);
  if (!size_only) ZopfliLengthsToSymbols(clcl, 19, 7, clsymbols);

  hclen = 15;
  /* Trim zeros. */
  while (hclen > 0 && clcounts[order[hclen + 4 - 1]] == 0) hclen--;

  if (!size_only) {
    AddBits(hlit, 5, bp, out, outsize);
    AddBits(hdist, 5, bp, out, outsize);
    AddBits(hclen, 4, bp, out, outsize);

    for (i = 0; i < hclen + 4; i++) {
      AddBits(clcl[order[i]], 3, bp, out, outsize);
    }

    for (i = 0; i < rle_size[0]; i++) {
      int symbol = clsymbols[rle[0][i]];
      AddHuffmanBits(symbol, clcl[rle[0][i]], bp, out, outsize);
      /* Extra bits. */
      if (rle[0][i] == 16) AddBits(rle_bits[0][i], 2, bp, out, outsize);
      else if (rle[0][i] == 17) AddBits(rle_bits[0][i], 3, bp, out, outsize);
      else if (rle[0][i] == 18) AddBits(rle_bits[0][i], 7, bp, out, outsize);
    }
  }

  result_size += 14;  /* hlit, hdist, hclen bits */
  result_size += (hclen + 4) * 3;  /* clcl bits */
  for(i = 0; i < 19; i++) {
    result_size += clcl[i] * clcounts[i];
  }
  /* Extra bits. */
  result_size += clcounts[16] * 2;
  result_size += clcounts[17] * 3;
  result_size += clcounts[18] * 7;

  /* Note: in case of "size_only" these are null pointers so no effect. */
  //free(rle);
  //free(rle_bits);

  return result_size;
}

private static void AddDynamicTree(int[] ll_lengths,
                           int[] d_lengths,
                           byte[] bp,
                           byte[][] out, int[] outsize) {
  int i;
  int best = 0;
  int bestsize = 0;

  for(i = 0; i < 8; i++) {
    int size = EncodeTree(ll_lengths, d_lengths,
                             (i & 1) !=0, (i & 2) !=0, (i & 4) !=0,
                             null, null, null);
    if (bestsize == 0 || size < bestsize) {
      bestsize = size;
      best = i;
    }
  }

  EncodeTree(ll_lengths, d_lengths,
             (best & 1) !=0, (best & 2) !=0, (best & 4) !=0,
             bp, out, outsize);
}

/**
Gives the exact size of the tree, in bits, as it will be encoded in DEFLATE.
*/
private static int CalculateTreeSize(int[] ll_lengths,
                                int[] d_lengths) {
  int result = 0;
  int i;

  for(i = 0; i < 8; i++) {
    int size = EncodeTree(ll_lengths, d_lengths,
                             (i & 1) !=0, (i & 2) !=0, (i & 4) !=0,
                             null, null, null);
    if (result == 0 || size < result) result = size;
  }

  return result;
}

/**
Adds all lit/len and dist codes from the lists as huffman symbols. Does not add
end code 256. expected_data_size is the uncompressed block size, used for
assert, but you can set it to 0 to not do the assertion.
*/
private static void AddLZ77Data(int[] litlens,
                        int[] dists,
                        int lstart, int lend,
                        int expected_data_size,
                        int[] ll_symbols, int[] ll_lengths,
                        int[] d_symbols, int[] d_lengths,
                        byte[] bp,
                        byte[][] out, int[] outsize) {
  int testlength = 0;
  int i;

  for (i = lstart; i < lend; i++) {
    int dist = dists[i];
    int litlen = litlens[i];
    if (dist == 0) {
      assert(litlen < 256);
      assert(ll_lengths[litlen] > 0);
      AddHuffmanBits(ll_symbols[litlen], ll_lengths[litlen], bp, out, outsize);
      testlength++;
    } else {
      int lls = ZopfliGetLengthSymbol(litlen);
      int ds = ZopfliGetDistSymbol(dist);
      assert(litlen >= 3 && litlen <= 288);
      assert(ll_lengths[lls] > 0);
      assert(d_lengths[ds] > 0);
      AddHuffmanBits(ll_symbols[lls], ll_lengths[lls], bp, out, outsize);
      AddBits(ZopfliGetLengthExtraBitsValue(litlen),
              ZopfliGetLengthExtraBits(litlen),
              bp, out, outsize);
      AddHuffmanBits(d_symbols[ds], d_lengths[ds], bp, out, outsize);
      AddBits(ZopfliGetDistExtraBitsValue(dist),
              ZopfliGetDistExtraBits(dist),
              bp, out, outsize);
      testlength += litlen;
    }
  }
  assert(expected_data_size == 0 || testlength == expected_data_size);
}

private static void GetFixedTree(int[] ll_lengths, int[] d_lengths) {
  int i;
  for (i = 0; i < 144; i++) ll_lengths[i] = 8;
  for (i = 144; i < 256; i++) ll_lengths[i] = 9;
  for (i = 256; i < 280; i++) ll_lengths[i] = 7;
  for (i = 280; i < 288; i++) ll_lengths[i] = 8;
  for (i = 0; i < 32; i++) d_lengths[i] = 5;
}

/**
Calculates size of the part after the header and tree of an LZ77 block, in bits.
*/
private static int CalculateBlockSymbolSize(int[] ll_lengths,
                                       int[] d_lengths,
                                       int[] litlens,
                                       int[] dists,
                                       int lstart, int lend) {
  int result = 0;
  int i;
  for (i = lstart; i < lend; i++) {
    if (dists[i] == 0) {
      result += ll_lengths[litlens[i]];
    } else {
      result += ll_lengths[ZopfliGetLengthSymbol(litlens[i])];
      result += d_lengths[ZopfliGetDistSymbol(dists[i])];
      result += ZopfliGetLengthExtraBits(litlens[i]);
      result += ZopfliGetDistExtraBits(dists[i]);
    }
  }
  result += ll_lengths[256]; /*end symbol*/
  return result;
}

private static int AbsDiff(int x, int y) {
  if (x > y)
    return x - y;
  else
    return y - x;
}

/**
Change the population counts in a way that the consequent Hufmann tree
compression, especially its rle-part will be more likely to compress this data
more efficiently. length containts the size of the histogram.
*/
private static void OptimizeHuffmanForRle(int length, int[] counts) {
  int i, k, stride;
  int symbol, sum, limit;
  boolean[] good_for_rle;

  /* 1) We don't want to touch the trailing zeros. We may break the
  rules of the format by adding more data in the distance codes. */
  for (; length >= 0; --length) {
    if (length == 0) {
      return;
    }
    if (counts[length - 1] != 0) {
      /* Now counts[0..length - 1] does not have trailing zeros. */
      break;
    }
  }
  /* 2) Let's mark all population counts that already can be encoded
  with an rle code.*/
  good_for_rle = new boolean[length];
  //for (i = 0; i < length; ++i) good_for_rle[i] = false;

  /* Let's not spoil any of the existing good rle codes.
  Mark any seq of 0's that is longer than 5 as a good_for_rle.
  Mark any seq of non-0's that is longer than 7 as a good_for_rle.*/
  symbol = counts[0];
  stride = 0;
  for (i = 0; i < length + 1; ++i) {
    if (i == length || counts[i] != symbol) {
      if ((symbol == 0 && stride >= 5) || (symbol != 0 && stride >= 7)) {
        for (k = 0; k < stride; ++k) {
          good_for_rle[i - k - 1] = true;
        }
      }
      stride = 1;
      if (i != length) {
        symbol = counts[i];
      }
    } else {
      ++stride;
    }
  }

  /* 3) Let's replace those population counts that lead to more rle codes. */
  stride = 0;
  limit = counts[0];
  sum = 0;
  for (i = 0; i < length + 1; ++i) {
    if (i == length || good_for_rle[i]
        /* Heuristic for selecting the stride ranges to collapse. */
        || AbsDiff(counts[i], limit) >= 4) {
      if (stride >= 4 || (stride >= 3 && sum == 0)) {
        /* The stride must end, collapse what we have, if we have enough (4). */
        int count = (sum + stride / 2) / stride;
        if (count < 1) count = 1;
        if (sum == 0) {
          /* Don't make an all zeros stride to be upgraded to ones. */
          count = 0;
        }
        for (k = 0; k < stride; ++k) {
          /* We don't want to change value at counts[i],
          that is already belonging to the next stride. Thus - 1. */
          counts[i - k - 1] = count;
        }
      }
      stride = 0;
      sum = 0;
      if (i < length - 3) {
        /* All interesting strides have a count of at least 4,
        at least when non-zeros. */
        limit = (counts[i] + counts[i + 1] +
                 counts[i + 2] + counts[i + 3] + 2) / 4;
      } else if (i < length) {
        limit = counts[i];
      } else {
        limit = 0;
      }
    }
    ++stride;
    if (i != length) {
      sum += counts[i];
    }
  }

  //free(good_for_rle);
}

/**
Calculates the bit lengths for the symbols for dynamic blocks. Chooses bit
lengths that give the smallest size of tree encoding + encoding of all the
symbols to have smallest output size. This are not necessarily the ideal Huffman
bit lengths.
*/
static void GetDynamicLengths(int[] litlens,
                                int[] dists,
                                int lstart, int lend, 
                                int[] ll_lengths, int[] d_lengths) {
  int[] ll_counts=new int[288];
  int[] d_counts=new int[32];

  ZopfliLZ77Counts(litlens, dists, lstart, lend, ll_counts, d_counts);
  OptimizeHuffmanForRle(288, ll_counts);
  OptimizeHuffmanForRle(32, d_counts);
  ZopfliCalculateBitLengths(ll_counts, 288, 15, ll_lengths);
  ZopfliCalculateBitLengths(d_counts, 32, 15, d_lengths);
  PatchDistanceCodesForBuggyDecoders(d_lengths);
}

public static double ZopfliCalculateBlockSize(int[] litlens,
                                int[] dists,
                                int lstart, int lend, int btype) {
  int[] ll_lengths=new int[288];
  int[] d_lengths=new int[32];

  double result = 3; /* bfinal and btype bits */

  assert(btype == 1 || btype == 2); /* This is not for uncompressed blocks. */

  if(btype == 1) {
    GetFixedTree(ll_lengths, d_lengths);
  } else {
    GetDynamicLengths(litlens, dists, lstart, lend, ll_lengths, d_lengths);
    result += CalculateTreeSize(ll_lengths, d_lengths);
  }

  result += CalculateBlockSymbolSize(
      ll_lengths, d_lengths, litlens, dists, lstart, lend);

  return result;
}

/**
Adds a deflate block with the given LZ77 data to the output.
options: global program options
btype: the block type, must be 1 or 2
final: whether to set the "final" bit on this block, must be the last block
litlens: literal/length array of the LZ77 data, in the same format as in
    ZopfliLZ77Store.
dists: distance array of the LZ77 data, in the same format as in
    ZopfliLZ77Store.
lstart: where to start in the LZ77 data
lend: where to end in the LZ77 data (not inclusive)
expected_data_size: the uncompressed block size, used for assert, but you can
  set it to 0 to not do the assertion.
bp: output bit pointer
out: dynamic output array to append to
outsize: dynamic output array size
*/
private static void AddLZ77Block(ZopfliOptions options, int btype, boolean finale,
                         int[] litlens,
                         int[] dists,
                         int lstart, int lend,
                         int expected_data_size,
                         byte[] bp,
                         byte[][] out, int[] outsize) {
  int[] ll_lengths=new int[288];
  int[] d_lengths=new int[32];
  int[] ll_symbols=new int[288];
  int[] d_symbols=new int[32];
  int detect_block_size = outsize[0];
  int compressed_size;
  int uncompressed_size = 0;
  int i;

  AddBit(finale, bp, out, outsize);
  AddBit((btype & 1)==1, bp, out, outsize);
  AddBit(((btype & 2) >> 1)==1, bp, out, outsize);

  if (btype == 1) {
    /* Fixed block. */
    GetFixedTree(ll_lengths, d_lengths);
  } else {
    /* Dynamic block. */
    int detect_tree_size;
    assert(btype == 2);

    GetDynamicLengths(litlens, dists, lstart, lend, ll_lengths, d_lengths);

    detect_tree_size = outsize[0];
    AddDynamicTree(ll_lengths, d_lengths, bp, out, outsize);
    if (options.verbose) {
      System.err.printf("treesize: %d\n", (int)(outsize[0] - detect_tree_size));
    }
  }

  ZopfliLengthsToSymbols(ll_lengths, 288, 15, ll_symbols);
  ZopfliLengthsToSymbols(d_lengths, 32, 15, d_symbols);

  detect_block_size = outsize[0];
  AddLZ77Data(litlens, dists, lstart, lend, expected_data_size,
              ll_symbols, ll_lengths, d_symbols, d_lengths,
              bp, out, outsize);
  /* End symbol. */
  AddHuffmanBits(ll_symbols[256], ll_lengths[256], bp, out, outsize);

  for (i = lstart; i < lend; i++) {
    uncompressed_size += dists[i] == 0 ? 1 : litlens[i];
  }
  compressed_size = outsize[0] - detect_block_size;
  if (options.verbose) {
    System.err.printf("compressed block size: %d (%dk) (unc: %d)\n",
           (int)compressed_size, (int)(compressed_size / 1024),
           (int)(uncompressed_size));
  }
}

private static void DeflateDynamicBlock(ZopfliOptions options, boolean finale,
                                byte[] in,
                                int instart, int inend,
                                byte[] bp,
                                byte[][] out, int[] outsize) {
  ZopfliBlockState s=new ZopfliBlockState();
  int blocksize = inend - instart;
  ZopfliLZ77Store store=new ZopfliLZ77Store();
  int btype = 2;

  ZopfliInitLZ77Store(store);

  s.options = options;
  s.blockstart = instart;
  s.blockend = inend;
//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  s.lmc = new ZopfliLongestMatchCache();
  ZopfliInitCache(blocksize, s.lmc);
//#endif

  ZopfliLZ77Optimal(s, in, instart, inend, store);

  /* For small block, encoding with fixed tree can be smaller. For large block,
  don't bother doing this expensive test, dynamic tree will be better.*/
  if (store.size[0] < 1000) {
    double dyncost, fixedcost;
    ZopfliLZ77Store fixedstore=new ZopfliLZ77Store();
    ZopfliInitLZ77Store(fixedstore);
    ZopfliLZ77OptimalFixed(s, in, instart, inend, fixedstore);
    dyncost = ZopfliCalculateBlockSize(store.litlens[0], store.dists[0],
        0, store.size[0], 2);
    fixedcost = ZopfliCalculateBlockSize(fixedstore.litlens[0], fixedstore.dists[0],
        0, fixedstore.size[0], 1);
    if (fixedcost < dyncost) {
      btype = 1;
      ZopfliCleanLZ77Store(store);
      store = fixedstore;
    } else {
      ZopfliCleanLZ77Store(fixedstore);
    }
  }

  AddLZ77Block(s.options, btype, finale,
               store.litlens[0], store.dists[0], 0, store.size[0],
               blocksize, bp, out, outsize);

//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  ZopfliCleanCache(s.lmc);
  //free(s.lmc);
//#endif
  ZopfliCleanLZ77Store(store);
}

private static void DeflateFixedBlock(ZopfliOptions options, boolean finale,
                              byte[] in,
                              int instart, int inend,
                              byte[] bp,
                              byte[][] out, int[] outsize) {
  ZopfliBlockState s=new ZopfliBlockState();
  int blocksize = inend - instart;
  ZopfliLZ77Store store=new ZopfliLZ77Store();

  ZopfliInitLZ77Store(store);

  s.options = options;
  s.blockstart = instart;
  s.blockend = inend;
//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  s.lmc = new ZopfliLongestMatchCache();
  ZopfliInitCache(blocksize, s.lmc);
//#endif

  ZopfliLZ77OptimalFixed(s, in, instart, inend, store);

  AddLZ77Block(s.options, 1, finale, store.litlens[0], store.dists[0], 0, store.size[0],
               blocksize, bp, out, outsize);

//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  ZopfliCleanCache(s.lmc);
  //free(s.lmc);
//#endif
  ZopfliCleanLZ77Store(store);
}

private static void DeflateNonCompressedBlock(ZopfliOptions options, boolean finale,
                                      byte[] in, int instart,
                                      int inend,
                                      byte[] bp,
                                      byte[][] out, int[] outsize) {
  int i;
  int blocksize = inend - instart;
  int nlen = ~blocksize;

  //(void)options;
  assert(blocksize < 65536);  /* Non compressed blocks are max this size. */

  AddBit(finale, bp, out, outsize);
  /* BTYPE 00 */
  AddBit(false, bp, out, outsize);
  AddBit(false, bp, out, outsize);

  /* Any bits of input up to the next byte boundary are ignored. */
  bp[0] = 0;

  ZOPFLI_APPEND_DATA(blocksize % 256, out, outsize);
  ZOPFLI_APPEND_DATA((blocksize / 256) % 256, out, outsize);
  ZOPFLI_APPEND_DATA(nlen % 256, out, outsize);
  ZOPFLI_APPEND_DATA((nlen / 256) % 256, out, outsize);

  for (i = instart; i < inend; i++) {
    ZOPFLI_APPEND_DATA(in[i], out, outsize);
  }
}

private static void DeflateBlock(ZopfliOptions options,
                         int btype, boolean finale,
                         byte[] in, int instart, int inend,
                         byte[] bp,
                         byte[][] out, int[] outsize) {
  if (btype == 0) {
    DeflateNonCompressedBlock(
        options, finale, in, instart, inend, bp, out, outsize);
  } else if (btype == 1) {
     DeflateFixedBlock(options, finale, in, instart, inend, bp, out, outsize);
  } else {
    assert (btype == 2);
    DeflateDynamicBlock(options, finale, in, instart, inend, bp, out, outsize);
  }
}

/**
Does squeeze strategy where first block splitting is done, then each block is
squeezed.
Parameters: see description of the ZopfliDeflate function.
*/
private static void DeflateSplittingFirst(ZopfliOptions options,
                                  int btype, boolean finale,
                                  byte[] in,
                                  int instart, int inend,
                                  byte[] bp,
                                  byte[][] out, int[] outsize) {
  int i;
  int[][] splitpoints = {{0}};
  int[] npoints = {0};
  if (btype == 0) {
    ZopfliBlockSplitSimple(in, instart, inend, 65535, splitpoints, npoints);
  } else if (btype == 1) {
    /* If all blocks are fixed tree, splitting into separate blocks only
    increases the total size. Leave npoints at 0, this represents 1 block. */
  } else {
    ZopfliBlockSplit(options, in, instart, inend,
                     options.blocksplittingmax, splitpoints, npoints);
  }

  for (i = 0; i <= npoints[0]; i++) {
    int start = i == 0 ? instart : splitpoints[0][i - 1];
    int end = i == npoints[0] ? inend : splitpoints[0][i];
    DeflateBlock(options, btype, i == npoints[0] && finale, in, start, end,
                 bp, out, outsize);
  }

  //free(splitpoints);
}

/**
Does squeeze strategy where first the best possible lz77 is done, and then based
on that data, block splitting is done.
Parameters: see description of the ZopfliDeflate function.
*/
private static void DeflateSplittingLast(ZopfliOptions options,
                                 int btype, boolean finale,
                                 byte[] in,
                                 int instart, int inend,
                                 byte[] bp,
                                 byte[][] out, int[] outsize) {
  int i;
  ZopfliBlockState s=new ZopfliBlockState();
  ZopfliLZ77Store store=new ZopfliLZ77Store();
  int[][] splitpoints = {{0}};
  int[] npoints = {0};

  if (btype == 0) {
    /* This function only supports LZ77 compression. DeflateSplittingFirst
       supports the special case of noncompressed data. Punt it to that one. */
    DeflateSplittingFirst(options, btype, finale,
                          in, instart, inend,
                          bp, out, outsize);
  }
  assert(btype == 1 || btype == 2);

  ZopfliInitLZ77Store(store);

  s.options = options;
  s.blockstart = instart;
  s.blockend = inend;
//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  s.lmc = new ZopfliLongestMatchCache();
  ZopfliInitCache(inend - instart, s.lmc);
//#endif

  if (btype == 2) {
    ZopfliLZ77Optimal(s, in, instart, inend, store);
  } else {
    assert (btype == 1);
    ZopfliLZ77OptimalFixed(s, in, instart, inend, store);
  }

  if (btype == 1) {
    /* If all blocks are fixed tree, splitting into separate blocks only
    increases the total size. Leave npoints at 0, this represents 1 block. */
  } else {
    ZopfliBlockSplitLZ77(options, store.litlens[0], store.dists[0], store.size[0],
                         options.blocksplittingmax, splitpoints, npoints);
  }

  for (i = 0; i <= npoints[0]; i++) {
    int start = i == 0 ? 0 : splitpoints[0][i - 1];
    int end = i == npoints[0] ? store.size[0] : splitpoints[0][i];
    AddLZ77Block(options, btype, i == npoints[0] && finale,
                 store.litlens[0], store.dists[0], start, end, 0,
                 bp, out, outsize);
  }

//#ifdef ZOPFLI_LONGEST_MATCH_CACHE
  ZopfliCleanCache(s.lmc);
  //free(s.lmc);
//#endif

  ZopfliCleanLZ77Store(store);
  //free(splitpoints);
}

/**
Deflate a part, to allow ZopfliDeflate() to use multiple master blocks if
needed.
It is possible to call this function multiple times in a row, shifting
instart and inend to next bytes of the data. If instart is larger than 0, then
previous bytes are used as the initial dictionary for LZ77.
This function will usually output multiple deflate blocks. If final is 1, then
the final bit will be set on the last block.
*/
public static void ZopfliDeflatePart(ZopfliOptions options, int btype, boolean finale,
                       byte[] in, int instart, int inend,
                       byte[] bp, byte[][] out,
                       int[] outsize) {
  if (options.blocksplitting) {
    if (options.blocksplittinglast) {
      DeflateSplittingLast(options, btype, finale, in, instart, inend,
                           bp, out, outsize);
    } else {
      DeflateSplittingFirst(options, btype, finale, in, instart, inend,
                            bp, out, outsize);
    }
  } else {
    DeflateBlock(options, btype, finale, in, instart, inend, bp, out, outsize);
  }
}

public static void ZopfliDeflate(ZopfliOptions options, int btype, boolean finale,
                   byte[] in, int insize,
                   byte[] bp, byte[][] out, int[] outsize) {
//#if ZOPFLI_MASTER_BLOCK_SIZE == 0
//  ZopfliDeflatePart(options, btype, finale, in, 0, insize, bp, out, outsize);
//#else
  int i = 0;
  while (i < insize) {
    boolean masterfinal = (i + ZOPFLI_MASTER_BLOCK_SIZE >= insize);
    boolean final2 = finale && masterfinal;
    int size = masterfinal ? insize - i : ZOPFLI_MASTER_BLOCK_SIZE;
    ZopfliDeflatePart(options, btype, final2,
                      in, i, i + size, bp, out, outsize);
    i += size;
  }
//#endif
  if (options.verbose) {
    System.err.printf(
            "Original Size: %d, Deflate: %d, Compression: %f%% Removed\n",
            (int)insize, (int)outsize[0],
            100.0 * (double)(insize - outsize[0]) / (double)insize);
  }
}
}