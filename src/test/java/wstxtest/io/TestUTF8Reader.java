package wstxtest.io;

import java.io.*;

import junit.framework.TestCase;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.io.UTF8Reader;

/**
 * Unit test created to verify fix to
 * <a href="http://jira.codehaus.org/browse/WSTX-143">WSTX-143</a>.
 *
 * @author Matt Gormley
 */
public class TestUTF8Reader extends TestCase
{
    @SuppressWarnings("resource")
    public void testDelAtBufferBoundary() throws IOException
    {
	final int BYTE_BUFFER_SIZE = 4;
	final int CHAR_BUFFER_SIZE = 1 + BYTE_BUFFER_SIZE; 
	final int INPUT_SIZE = 4 * BYTE_BUFFER_SIZE; // could be of arbitrary size
	final byte CHAR_FILLER = 32; // doesn't even matter, just need an ascii char
	final byte CHAR_DEL = 127;
	
	// Create input that will cause the array index out of bounds exception
	byte[] inputBytes = new byte[INPUT_SIZE];
	for (int i=0; i < inputBytes.length; i++) {
	    inputBytes[i] = CHAR_FILLER;
	}
	inputBytes[BYTE_BUFFER_SIZE - 1] = CHAR_DEL;
	InputStream in = new ByteArrayInputStream(inputBytes);
	
	// Create the UTF8Reader
	ReaderConfig cfg = ReaderConfig.createFullDefaults();
	byte[] byteBuffer = new byte[BYTE_BUFFER_SIZE];
	UTF8Reader reader = new UTF8Reader(cfg,in, byteBuffer, 0, 0, false);
	
	// Run the reader on the input
	char[] charBuffer = new char[CHAR_BUFFER_SIZE];
	reader.read(charBuffer, 0, charBuffer.length);		
    }
}
