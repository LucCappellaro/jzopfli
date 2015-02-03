/*
Copyright 2013 Google Inc. All Rights Reserved.

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
import lu.luz.jzopfli.ZopfliH.*;//#include "zlib_container.h"
import static lu.luz.jzopfli.Util.*;//#include "util.h"

//#include <stdio.h>

import static lu.luz.jzopfli.Deflate.*;//#include "deflate.h"
final class Zlib_container extends Zlib_containerH{

/** Calculates the adler32 checksum of the data */
private static int adler32(byte[] data, int size)
{
  final int sums_overflow = 5550;
  int s1 = 1;
  int s2 = 1 >> 16;
  int i=0;
  while (size > 0) {
	  int amount = size > sums_overflow ? sums_overflow : size;
    size -= amount;
    while (amount > 0) {
      s1 += data[i++]&0xff;
      s2 += s1;
      amount--;
    }
    s1 %= 65521;
    s2 %= 65521;
  }

  return (s2 << 16) | s1;
}

public static void ZopfliZlibCompress(ZopfliOptions options,
                        byte[] in, int insize,
                        byte[][] out, int[] outsize) {
  byte[] bitpointer = {0};
  int checksum = adler32(in, insize);
  int cmf = 120;  /* CM 8, CINFO 7. See zlib spec.*/
  int flevel = 0;
  int fdict = 0;
  int cmfflg = 256 * cmf + fdict * 32 + flevel * 64;
  int fcheck = 31 - cmfflg % 31;
  cmfflg += fcheck;

  ZOPFLI_APPEND_DATA((byte)(cmfflg / 256), out, outsize);
  ZOPFLI_APPEND_DATA((byte)cmfflg, out, outsize);

  ZopfliDeflate(options, 2 /* dynamic block */, true /* final */,
                in, insize, bitpointer, out, outsize);

  ZOPFLI_APPEND_DATA((byte)(checksum >> 24), out, outsize);
  ZOPFLI_APPEND_DATA((byte)(checksum >> 16), out, outsize);
  ZOPFLI_APPEND_DATA((byte)(checksum >> 8), out, outsize);
  ZOPFLI_APPEND_DATA((byte)checksum, out, outsize);

  if (options.verbose) {
    System.err.printf(
            "Original Size: %d, Zlib: %d, Compression: %f%% Removed\n",
            insize, outsize[0],
            100.0 * (double)(insize - outsize[0]) / (double)insize);
  }
}
}