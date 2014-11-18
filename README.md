jzopfli
=======
Java port of [Zopfli](https://code.google.com/p/zopfli/), a zlib-compatible compression library.

Zopfli Compression Algorithm is a new zlib (gzip, deflate) compatible compressor. 
This compressor takes more time (~100x slower), 
but compresses around 5% better than zlib 
and better than any other zlib-compatible compressor we have found.

Note:
This is Work in progress. which is **not** ready for production.

The source code is kept as close as possible to the original C version
in order to allow side-by-side debugging and integrate patches easier.