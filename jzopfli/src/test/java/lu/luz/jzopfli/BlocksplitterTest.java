package lu.luz.jzopfli;

import java.io.IOException;

import lu.luz.jzopfli.ZopfliH.ZopfliOptions;

import org.junit.Test;

public class BlocksplitterTest {
	
	@Test
	public void testZopfliBlockSplit() throws IOException  {
		ZopfliOptions options=new ZopfliOptions();
		byte[] in={};
		int instart=0;
		int inend=0;
		int maxblocks=0;
		int[][] splitpoints={{}};
		int[] npoints={0};
		BlockSplitter.ZopfliBlockSplit(options, in, instart, inend, maxblocks, splitpoints, npoints);
	}	
	
	@Test
	public void testZopfliBlockSplitLZ77() throws IOException  {
		ZopfliOptions options=new ZopfliOptions();
		char[] litlens={};
		char[] dists={};
		int llsize=0;
		int maxblocks=0;
		int[][] splitpoints={};
		int[] npoints={};
		BlockSplitter.ZopfliBlockSplitLZ77(options, litlens, dists, llsize, maxblocks, splitpoints, npoints);
	}
	
	@Test
	public void testZopfliBlockSplitSimple() throws IOException  {
		byte[] in={};
		int instart=0;
		int inend=0;		
		int blocksize=0;
		int[][] splitpoints={{}};
		int[] npoints={};
		BlockSplitter.ZopfliBlockSplitSimple(in, instart, inend, blocksize, splitpoints, npoints);
	}	
}
