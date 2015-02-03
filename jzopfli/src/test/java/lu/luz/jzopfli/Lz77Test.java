package lu.luz.jzopfli;

import static lu.luz.jzopfli.Lz77.ZopfliInitLZ77Store;

import java.io.IOException;

import lu.luz.jzopfli.Lz77H.ZopfliBlockState;
import lu.luz.jzopfli.Lz77H.ZopfliLZ77Store;

import org.junit.Test;

public class Lz77Test {
	
	@Test
	public void testZopfliLZ77Greedy() throws IOException  {
		ZopfliBlockState s=new ZopfliBlockState();
		byte[] in={};
		int instart=0;
		int inend=0;
		ZopfliLZ77Store store=new ZopfliLZ77Store();
		ZopfliInitLZ77Store(store);
		Lz77.ZopfliLZ77Greedy(s, in, instart, inend, store);
	}
	
	
	@Test
	public void testZopfliStoreLitLenDist() throws IOException  {
		char length=0;
		char dist=0;
		ZopfliLZ77Store store=new ZopfliLZ77Store();
		ZopfliInitLZ77Store(store);
		Lz77.ZopfliStoreLitLenDist(length, dist, store);
	}
	
}
