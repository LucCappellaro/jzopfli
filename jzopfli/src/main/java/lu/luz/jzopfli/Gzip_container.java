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
import lu.luz.jzopfli.ZopfliH.*;//#include "gzip_container.h"
import static lu.luz.jzopfli.Util.*;//#include "util.h"

//#include <stdio.h>

import static lu.luz.jzopfli.Deflate.*;//#include "deflate.h"
class Gzip_container extends Gzip_containerH{
/** Table of CRCs of all 8-bit messages. */
private static long[] crc_table=new long[256];

/** Flag: has the table been computed? Initially false. */
private static boolean crc_table_computed = false;

/** Makes the table for a fast CRC. */
private static void MakeCRCTable() {
  long c;
  int n, k;
  for (n = 0; n < 256; n++) {
    c = (long) n;
    for (k = 0; k < 8; k++) {
      if ((c & 1) != 0) {
        c = 0xedb88320L ^ (c >> 1);
      } else {
        c = c >> 1;
      }
    }
    crc_table[n] = c;
  }
  crc_table_computed = true;
}


/**
Updates a running crc with the bytes buf[0..len-1] and returns
the updated crc. The crc should be initialized to zero.
*/
private static long UpdateCRC(long crc,
                               byte[] buf, int len) {
  long c = crc ^ 0xffffffffL;
  int n;

  if (!crc_table_computed)
    MakeCRCTable();
  for (n = 0; n < len; n++) {
    c = crc_table[(int)(c ^ buf[n]) & 0xff] ^ (c >> 8);
  }
  return c ^ 0xffffffffL;
}

/** Returns the CRC of the bytes buf[0..len-1]. */
private static long CRC(byte[] buf, int len) {
  return UpdateCRC(0L, buf, len);
}

/**
Compresses the data according to the gzip specification.
*/
public static void ZopfliGzipCompress(ZopfliOptions options,
                        byte[] in, int insize,
                        byte[][] out, int[] outsize) {
  long crcvalue = CRC(in, insize);
  byte[] bp = {0};

  ZOPFLI_APPEND_DATA(31, out, outsize);  /* ID1 */
  ZOPFLI_APPEND_DATA(139, out, outsize);  /* ID2 */
  ZOPFLI_APPEND_DATA(8, out, outsize);  /* CM */
  ZOPFLI_APPEND_DATA(0, out, outsize);  /* FLG */
  /* MTIME */
  ZOPFLI_APPEND_DATA(0, out, outsize);
  ZOPFLI_APPEND_DATA(0, out, outsize);
  ZOPFLI_APPEND_DATA(0, out, outsize);
  ZOPFLI_APPEND_DATA(0, out, outsize);

  ZOPFLI_APPEND_DATA(2, out, outsize);  /* XFL, 2 indicates best compression. */
  ZOPFLI_APPEND_DATA(3, out, outsize);  /* OS follows Unix conventions. */

  ZopfliDeflate(options, 2 /* Dynamic block */, true,
                in, insize, bp, out, outsize);

  /* CRC */
  ZOPFLI_APPEND_DATA((int)(crcvalue % 256), out, outsize);
  ZOPFLI_APPEND_DATA((int)((crcvalue >> 8) % 256), out, outsize);
  ZOPFLI_APPEND_DATA((int)((crcvalue >> 16) % 256), out, outsize);
  ZOPFLI_APPEND_DATA((int)((crcvalue >> 24) % 256), out, outsize);

  /* ISIZE */
  ZOPFLI_APPEND_DATA(insize % 256, out, outsize);
  ZOPFLI_APPEND_DATA((insize >> 8) % 256, out, outsize);
  ZOPFLI_APPEND_DATA((insize >> 16) % 256, out, outsize);
  ZOPFLI_APPEND_DATA((insize >> 24) % 256, out, outsize);

  if (options.verbose) {
    System.err.printf(
            "Original Size: %d, Gzip: %d, Compression: %f%% Removed\n",
            (int)insize, outsize[0],
            100.0 * (double)(insize - outsize[0]) / (double)insize);
  }
}
}