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
Zopfli compressor program. It can output gzip-, zlib- or deflate-compatible
data. By default it creates a .gz file. This tool can only compress, not
decompress. Decompression can be done by any standard gzip, zlib or deflate
decompressor.
*/

//#include <assert.h>
import java.nio.file.*;import java.io.*;//#include <stdio.h>
//#include <stdlib.h>
//#include <string.h>

import static lu.luz.jzopfli.ZopfliH.ZopfliFormat.*;//#include "deflate.h"
import static lu.luz.jzopfli.Util.*;//#include "gzip_container.h"
import static lu.luz.jzopfli.Zopfli_lib.*;//#include "zlib_container.h"
public final class Zopfli_bin{
/*
Loads a file into a memory array.
*/
private static void LoadFile(String filename,
                     byte[][] out, int[] outsize) throws IOException {
  File file;

  out[0] = null;
  outsize[0] = 0;
  file = new File(filename);


  //fseek(file , 0 , SEEK_END);
  outsize[0] = (int)file.length();
  //rewind(file);

  out[0] = new byte[outsize[0]];

  if (outsize[0]!=0 && out[0]!=null) {
    int testsize = (out[0]=Files.readAllBytes(file.toPath())).length;
    if (testsize != outsize[0]) {
      /* It could be a directory */
      out[0]=null;
      out = null;
      outsize[0] = 0;
    }
  }

  assert(!(outsize[0]!=0) || out!=null);  /* If size is not zero, out must be allocated. */
  file=null;
}

/**
Saves a file from a memory array, overwriting the file if it existed.
*/
private static void SaveFile(String filename,
                     byte[][] in, int[] insize) throws IOException {
  FileOutputStream file = new FileOutputStream(filename);
  assert(file!=null);
  file.write(in[0], 0, insize[0]);
  file.close();
}

/**
outfilename: filename to write output to, or 0 to write to stdout instead
*/
private static void CompressFile(ZopfliOptions options,
                         ZopfliFormat output_type,
                         String infilename,
                         String outfilename) throws IOException {
  byte[][] in={{0}};
  int[] insize={0};
  byte[][] out = {{0}};
  int[] outsize = {0};
  LoadFile(infilename, in, insize);
  if (insize[0] == 0) {
	  System.err.printf("Invalid filename: %s\n", infilename);
    return;
  }

  ZopfliCompress(options, output_type, in[0], insize[0], out, outsize);

  if (outfilename!=null) {
    SaveFile(outfilename, out, outsize);
  } else {
    int i;
    for (i = 0; i < outsize[0]; i++) {
      /* Works only if terminal does not convert newlines. */
      System.out.write(out[0][i]);
    }
  }

  out=null;
  in=null;
}

/**
Add two strings together. Size does not matter. Result must be freed.
*/
private static String AddStrings(String str1, String str2) {
  int len = str1.length() + str2.length();
  StringBuilder result = new StringBuilder(len + 1);

  result.append(str1);
  result.append(str2);
  return result.toString();
}

private static boolean StringsEqual(String str1, String str2) {
  return str1.equals(str2);
}

public static void main(String argv[]) throws Exception {
  ZopfliOptions options=new ZopfliOptions();
  ZopfliFormat output_type = ZOPFLI_FORMAT_GZIP;
  String filename = null;
  boolean output_to_stdout = false;
  int i;

  ZopfliInitOptions(options);

  for (i = 0; i < argv.length; i++) {
    String arg = argv[i];
    if (StringsEqual(arg, "-v")) options.verbose = true;
    else if (StringsEqual(arg, "-c")) output_to_stdout = true;
    else if (StringsEqual(arg, "--deflate")) {
      output_type = ZOPFLI_FORMAT_DEFLATE;
    }
    else if (StringsEqual(arg, "--zlib")) output_type = ZOPFLI_FORMAT_ZLIB;
    else if (StringsEqual(arg, "--gzip")) output_type = ZOPFLI_FORMAT_GZIP;
    else if (StringsEqual(arg, "--splitlast")) options.blocksplittinglast = true;
    else if (arg.charAt(0)=='-' && arg.charAt(1) == '-' && arg.charAt(2) == 'i'
        && arg.charAt(3) >= '0' && arg.charAt(3) <= '9') {
      options.numiterations = Integer.valueOf(arg.substring(3));
    }
    else if (StringsEqual(arg, "-h")) {
      System.err.printf(
          "Usage: zopfli [OPTION]... FILE\n"+
          "  -h    gives this help\n"+
          "  -c    write the result on standard output, instead of disk"+
          " filename + '.gz'\n"+
          "  -v    verbose mode\n"+
          "  --i#  perform # iterations (default 15). More gives"+
          " more compression but is slower."+
          " Examples: --i10, --i50, --i1000\n");
      System.err.printf(
          "  --gzip        output to gzip format (default)\n"+
          "  --zlib        output to zlib format instead of gzip\n"+
          "  --deflate     output to deflate format instead of gzip\n"+
          "  --splitlast   do block splitting last instead of first\n");
      return;
    }
  }

  if (options.numiterations < 1) {
	  System.err.println("Error: must have 1 or more iterations");
	  return;
  }

  for (i = 0; i < argv.length; i++) {
    if (argv[i].charAt(0) != '-') {
      String outfilename;
      filename = argv[i];
      if (output_to_stdout) {
        outfilename = null;
      } else if (output_type == ZOPFLI_FORMAT_GZIP) {
        outfilename = AddStrings(filename, ".gz");
      } else if (output_type == ZOPFLI_FORMAT_ZLIB) {
        outfilename = AddStrings(filename, ".zlib");
      } else {
        assert(output_type == ZOPFLI_FORMAT_DEFLATE);
        outfilename = AddStrings(filename, ".deflate");
      }
      if (options.verbose && outfilename!=null) {
    	  System.err.printf("Saving to: %s\n", outfilename);
      }
      CompressFile(options, output_type, filename, outfilename);
      //free(outfilename);
    }
  }

  if (filename==null) {
	  System.err.printf(
            "Please provide filename\nFor help, type: %s -h\n", "java -jar "+new java.io.File(Zopfli_bin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName());
  }

  return;
}
}