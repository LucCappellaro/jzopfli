package lu.luz.jzopfli;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

public class UtilTest {
	
	@Test
	public void testByteSize0() throws IOException  {
		int value=42;
		byte[][] data={{}};
		int[] size={0};
		UtilH.ZOPFLI_APPEND_DATA(value, data, size);		
		
		assertEquals(1, size[0]);
		assertEquals(1, data[0].length);
		assertEquals(value, data[0][0]);
	}
	
	@Test
	public void testByteSize1() throws IOException  {
		int value=42;
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
		int value=42;
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
		int value=42;
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
		int value=42;
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
	
	
}
