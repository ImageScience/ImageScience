package imagescience.utility;

import java.io.IOException;
import java.io.Reader;

/** Reads text line-by-line from a character stream using buffering for efficiency. Inspired by {@code java.io.BufferedReader} but with methods providing information about how many characters were actually read during the last call and how many lines were read in total. */
public class LineReader { // Inspired by java.io.BufferedReader
	
    private final Reader reader;
	
    private final char cb[] = new char[8192];
	
    private int nChars = 0, nextChar = 0;
	
    private boolean skip = false;
	
	/** Constructs a buffered line reader from the given character stream reader.
		
		@param reader The character stream reader.
	*/
    public LineReader(final Reader reader) {
		
		this.reader = reader;
    }
	
    private void fill() throws IOException {
		
		int n;
		do {
			n = reader.read(cb, 0, cb.length);
		} while (n == 0);
		
		if (n > 0) {
			nChars = n;
			nextChar = 0;
		}
	}
	
	/** Reads a line of text. A line is considered to be terminated by any of a line feed, a carriage return, or a carriage return followed immediately by a line feed.
		
		@return A {@code String} containing the contents of the line, not including any line-termination characters, or {@code null} if the end of the stream has been reached.
		
		@throws IOException If an I/O error occurs.
	*/
    public String read() throws IOException {
		
		StringBuffer sb = null;
		int startChar = 0;
		++lineCount;
		
		while (true) {
			
			if (nextChar >= nChars) fill();
			
			if (nextChar >= nChars) { // EOF
				if (sb != null && sb.length() > 0) {
					return sb.toString();
				} else return null;
			}
			
			boolean eol = false;
			char c = 0;
			int i;
			
			if (skip && (cb[nextChar] == '\n')) {
				++charCount;
				++nextChar;
			}
			skip = false;
			
			for (i = nextChar; i < nChars; ++i) {
				++charCount;
				c = cb[i];
				if ((c == '\n') || (c == '\r')) {
					eol = true;
					break;
				}
			}
			
			startChar = nextChar;
			nextChar = i;
			
			if (eol) {
				String str;
				if (sb == null) {
					str = new String(cb, startChar, i - startChar);
				} else {
					sb.append(cb, startChar, i - startChar);
					str = sb.toString();
				}
				++nextChar;
				if (c == '\r') {
					skip = true;
				}
				return str;
			}
			
			if (sb == null) sb = new StringBuffer(100);
			sb.append(cb, startChar, i - startChar);
		}
    }
	
	private long charCount = 0, lineCount = 0;
	
	/** Returns the number of characters read using this reader.
		
		@return The number of characters read using this reader.
	*/
	public long chars() { return charCount; }
	
	/** Returns the number of lines read using this reader.
		
		@return The number of lines read using this reader.
	*/
	public long lines() { return lineCount; }
	
	/** Closes the character stream reader and releases any system resources associated with it.
		
		@throws IOException If an I/O error occurs.
	*/
    public void close() throws IOException {
		
		if (reader != null) reader.close();
    }
	
}
