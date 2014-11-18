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
//#ifndef ZOPFLI_DEFLATE_H_
//#define ZOPFLI_DEFLATE_H_

/**
Functions to compress according to the DEFLATE specification, using the
"squeeze" LZ77 compression backend.
*/

//#include "zopfli.h"
abstract class DeflateH{
//#ifdef __cplusplus
//extern "C" {
//#endif

/*
Compresses according to the deflate specification and append the compressed
result to the output.
This function will usually output multiple deflate blocks. If final is 1, then
the final bit will be set on the last block.

options: global program options
btype: the deflate block type. Use 2 for best compression.
  -0: non compressed blocks (00)
  -1: blocks with fixed tree (01)
  -2: blocks with dynamic tree (10)
final: whether this is the last section of the input, sets the final bit to the
  last deflate block.
in: the input bytes
insize: number of input bytes
bp: bit pointer for the output array. This must initially be 0, and for
  consecutive calls must be reused (it can have values from 0-7). This is
  because deflate appends blocks as bit-based data, rather than on byte
  boundaries.
out: pointer to the dynamic output array to which the result is appended. Must
  be freed after use.
outsize: pointer to the dynamic output array size.
*/
//void ZopfliDeflate(ZopfliOptions options, int btype, int finaL,
//                   byte[] in, int insize,
//                   byte[] bp, OutputStream out, int[] outsize);

/*
Like ZopfliDeflate, but allows to specify start and end byte with instart and
inend. Only that part is compressed, but earlier bytes are still used for the
back window.
*/
//void ZopfliDeflatePart(ZopfliOptions options, int btype, int finaL,
//                       byte[] in, int instart, int inend,
//                       byte[] bp, OutputStream out,
//                       int[] outsize);

/*
Calculates block size in bits.
litlens: lz77 lit/lengths
dists: ll77 distances
lstart: start of block
lend: end of block (not inclusive)
*/
//double ZopfliCalculateBlockSize(short[] litlens,
//                                short[] dists,
//                                int lstart, int lend, int btype);

//#ifdef __cplusplus
//}  // extern "C"
//#endif

//#endif  /* ZOPFLI_DEFLATE_H_ */
}