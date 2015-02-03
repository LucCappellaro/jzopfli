package lu.luz.jzopfli;

import static lu.luz.jzopfli.Cache.ZopfliInitCache;
import lu.luz.jzopfli.CacheH.ZopfliLongestMatchCache;

import org.junit.Test;

public class CacheTest {
	
	@Test
	public void testZopfliCacheToSublen() throws Exception  {
		ZopfliLongestMatchCache lmc=new ZopfliLongestMatchCache();
		ZopfliInitCache(1, lmc);
		int pos=0;
		char length=0;
		char[] sublen={};
		Cache.ZopfliCacheToSublen(lmc, pos, length, sublen);
	}
	
	@Test
	public void testZopfliCleanCache() throws Exception  {
		ZopfliLongestMatchCache lmc=new ZopfliLongestMatchCache();
		Cache.ZopfliCleanCache(lmc);
	}
	
	@Test
	public void testZopfliInitCache() throws Exception  {
		int blocksize=0;
		ZopfliLongestMatchCache lmc=new ZopfliLongestMatchCache();
		Cache.ZopfliInitCache(blocksize, lmc);
	}
	
	@Test
	public void testZopfliMaxCachedSublen() throws Exception  {
		ZopfliLongestMatchCache lmc=new ZopfliLongestMatchCache();
		ZopfliInitCache(1, lmc);
		int pos=0;
		int length=0;
		Cache.ZopfliMaxCachedSublen(lmc, pos, length);
	}
	
	@Test
	public void testZopfliSublenToCache() throws Exception  {
		char[] sublen={};
		int pos=0;
		char length=0;
		ZopfliLongestMatchCache lmc=new ZopfliLongestMatchCache();
		Cache.ZopfliSublenToCache(sublen, pos, length, lmc);
	}
}
