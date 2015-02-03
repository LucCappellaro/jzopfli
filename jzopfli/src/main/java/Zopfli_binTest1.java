package lu.luz.jzopfli;

import org.junit.Ignore;
import org.junit.Test;

public class Zopfli_binTest {
	
	@Test
	public void testHelp() throws Exception  {
		String[] args={"-h"};
		Zopfli_bin.main(args);
	}
	
	@Test
	public void testDeflate() throws Exception  {
		String[] args={"--deflate", "src/test/resources/100.txt"};
		Zopfli_bin.main(args);
	}
	
	@Test
	public void testDeflate2() throws Exception  {
		String[] args={"-v", "--deflate", "src/test/resources/1musk10.txt"};
		Zopfli_bin.main(args);		
	}
	
	@Test
	public void testDeflate3() throws Exception  {
		String[] args={"-v", "--gzip", "src/test/resources/1musk10.txt"};
		Zopfli_bin.main(args);		
	}
	
	@Test
	public void testGzip() throws Exception  {
		String[] args={"--gzip", "src/test/resources/100.txt"};
		Zopfli_bin.main(args);
	}
	
	@Test
	public void testZlip() throws Exception  {
		String[] args={"--zlib", "src/test/resources/100.txt"};
		Zopfli_bin.main(args);
	}
}
