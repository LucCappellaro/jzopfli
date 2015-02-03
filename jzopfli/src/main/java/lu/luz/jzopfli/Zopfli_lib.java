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
import static lu.luz.jzopfli.ZopfliH.ZopfliFormat.*;//#include "zopfli.h"

import static lu.luz.jzopfli.Deflate.*;//#include "deflate.h"
import static lu.luz.jzopfli.Gzip_container.*;//#include "gzip_container.h"
import static lu.luz.jzopfli.Zlib_container.*;//#include "zlib_container.h"

//#include <assert.h>
public final class Zopfli_lib extends ZopfliH{
public static void ZopfliCompress(final ZopfliOptions options, ZopfliFormat output_type,
                    final byte[] in, int insize,
                    byte[][] out, int[] outsize) {
  if (output_type == ZOPFLI_FORMAT_GZIP) {
    ZopfliGzipCompress(options, in, insize, out, outsize);
  } else if (output_type == ZOPFLI_FORMAT_ZLIB) {
    ZopfliZlibCompress(options, in, insize, out, outsize);
  } else if (output_type == ZOPFLI_FORMAT_DEFLATE) {
    byte[] bp = {0};
    ZopfliDeflate(options, 2 /* Dynamic block */, true,
                  in, insize, bp, out, outsize);
  } else {
    assert(false);
  }
}
}