package lu.luz.jzopfli;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

public class UtilTest {
	
	@Test
	public void testByteSize0() throws IOException  {
		byte value=42;
		byte[][] data={{}};
		int[] size={0};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(1, size[0]);
		assertEquals(1, data[0].length);
		assertEquals(value, data[0][0]);
	}
	
	@Test
	public void testByteSize1() throws IOException  {
		byte value=42;
		byte[][] data={{1}};
		int[] size={1};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(2, size[0]);
		assertEquals(2, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(value, data[0][1]);		
	}
	
	@Test
	public void testByteSize2() throws IOException  {
		byte value=42;
		byte[][] data={{1,2}};
		int[] size={2};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(3, size[0]);
		assertEquals(4, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(2, data[0][1]);
		assertEquals(value, data[0][2]);	
	}	
	
	@Test
	public void testByteSize4a() throws IOException  {
		byte value=42;
		byte[][] data={{1,2,3,0}};
		int[] size={3};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(4, size[0]);
		assertEquals(4, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(2, data[0][1]);
		assertEquals(3, data[0][2]);
		assertEquals(value, data[0][3]);	
	}
	
	@Test
	public void testByteSize4b() throws IOException  {
		byte value=42;
		byte[][] data={{1,2,3,4}};
		int[] size={4};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(5, size[0]);
		assertEquals(8, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(2, data[0][1]);
		assertEquals(3, data[0][2]);
		assertEquals(4, data[0][3]);
		assertEquals(value, data[0][4]);	
	}
	
	///////////////////////////////////////
	
	
	@Test
	public void testIntSize0() throws IOException  {
		int value=42;
		int[][] data={{}};
		int[] size={0};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(1, size[0]);
		assertEquals(1, data[0].length);
		assertEquals(value, data[0][0]);
	}
	
	@Test
	public void testIntSize1() throws IOException  {
		int value=42;
		int[][] data={{1}};
		int[] size={1};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(2, size[0]);
		assertEquals(2, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(value, data[0][1]);		
	}
	
	@Test
	public void testIntSize2() throws IOException  {
		int value=42;
		int[][] data={{1,2}};
		int[] size={2};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(3, size[0]);
		assertEquals(4, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(2, data[0][1]);
		assertEquals(value, data[0][2]);	
	}	
	
	@Test
	public void testIntSize4a() throws IOException  {
		int value=42;
		int[][] data={{1,2,3,0}};
		int[] size={3};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(4, size[0]);
		assertEquals(4, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(2, data[0][1]);
		assertEquals(3, data[0][2]);
		assertEquals(value, data[0][3]);	
	}
	
	@Test
	public void testSize4b() throws IOException  {
		int value=42;
		int[][] data={{1,2,3,4}};
		int[] size={4};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(5, size[0]);
		assertEquals(8, data[0].length);
		assertEquals(1, data[0][0]);
		assertEquals(2, data[0][1]);
		assertEquals(3, data[0][2]);
		assertEquals(4, data[0][3]);
		assertEquals(value, data[0][4]);	
	}

	@Test
	public void testZopfliGetDistExtraBits() throws IOException {
		assertEquals(0, Util.ZopfliGetDistExtraBits(5 - 1));
		assertEquals(1, Util.ZopfliGetDistExtraBits(9 - 1));
		assertEquals(2, Util.ZopfliGetDistExtraBits(17 - 1));
		assertEquals(3, Util.ZopfliGetDistExtraBits(33 - 1));
		assertEquals(4, Util.ZopfliGetDistExtraBits(65 - 1));
		assertEquals(5, Util.ZopfliGetDistExtraBits(129 - 1));
		assertEquals(6, Util.ZopfliGetDistExtraBits(257 - 1));
		assertEquals(7, Util.ZopfliGetDistExtraBits(513 - 1));
		assertEquals(8, Util.ZopfliGetDistExtraBits(1025 - 1));
		assertEquals(9, Util.ZopfliGetDistExtraBits(2049 - 1));
		assertEquals(10, Util.ZopfliGetDistExtraBits(4097 - 1));
		assertEquals(11, Util.ZopfliGetDistExtraBits(8193 - 1));
		assertEquals(12, Util.ZopfliGetDistExtraBits(16385 - 1));
		assertEquals(13, Util.ZopfliGetDistExtraBits(16385 + 1));
	}
}
