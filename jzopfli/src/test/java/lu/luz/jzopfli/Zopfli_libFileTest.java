package lu.luz.jzopfli;

import static lu.luz.jzopfli.Util.ZopfliInitOptions;
import static lu.luz.jzopfli.ZopfliH.ZopfliFormat.ZOPFLI_FORMAT_DEFLATE;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import lu.luz.jzopfli.ZopfliH.ZopfliFormat;
import lu.luz.jzopfli.ZopfliH.ZopfliOptions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Zopfli_libFileTest {
	private byte[] input;

	public Zopfli_libFileTest(String input) throws IOException {
		this.input = Files.readAllBytes(Paths.get(input));
	}

	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
		{"src/test/resources/1musk10.txt"},
		{"src/test/resources/101.EXE"},
		{"src/test/resources/lena3.tif"},
		});
	}

	@Test
	public void testDefalte() throws Exception {
		byte[] ouput = compressZopfli(input);
		byte[] decompressed = decompress(ouput);
		assertArrayEquals(input, decompressed);
	}

	private static byte[] compressZopfli(byte[] in) {
		ZopfliOptions options = new ZopfliOptions();
		ZopfliFormat output_type = ZOPFLI_FORMAT_DEFLATE;
		ZopfliInitOptions(options);
		int insize = in.length;
		byte[][] out = { { 0 } };
		int[] outsize = { 0 };

		Zopfli_lib.ZopfliCompress(options, output_type, in, insize, out, outsize);
		byte[] result = Arrays.copyOf(out[0], outsize[0]);
		//System.out.println("Zopfli\t"+DatatypeConverter.printHexBinary(result));
		return result;
	}

	private static byte[] compress(byte[] in) {
		Deflater compresser = new Deflater();
		byte[] out = new byte[in.length];
		compresser.setInput(in);
		compresser.finish();
		int outsize = compresser.deflate(out);
		compresser.end();
		byte[] result = new byte[outsize-2];
		System.arraycopy(out, 2, result, 0, outsize-2);
		//System.out.println("Java\t"+DatatypeConverter.printHexBinary(result));
		return result;
	}

	private static byte[] decompress(byte[] data) throws DataFormatException {
		byte[] container = new byte[2 + data.length];
		container[0] = (byte) 0x78;
		container[1] = (byte) 0x9C;
		System.arraycopy(data, 0, container, 2, data.length);
		Inflater decompresser = new Inflater();
		decompresser.setInput(container);
		byte[] result = new byte[data.length * 100];
		int resultLength = decompresser.inflate(result);
		decompresser.end();
		return Arrays.copyOf(result, resultLength);
	}
}
