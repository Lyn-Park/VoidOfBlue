package net.vob.util.logging;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * This class augments a {@link Logger} object with the functionalities and
 * properties of an output stream of bytes.
 * 
 * @author Lyn-Park
 */
@SuppressWarnings("NonConstantLogger")
public class LoggerOutputStream extends OutputStream {
    private static final int MAX_BYTES = Integer.MAX_VALUE >> 4;
    
    private byte[] bytes = new byte[0];
    private int pointer = 0;
    private final Logger logger;
    private final Level level;
    
    public LoggerOutputStream(Logger logger, Level level) {
        this.logger = logger;
        this.level = level;
    }
    
    private void tryResize(int target) throws IndexOutOfBoundsException {
        while (target > bytes.length) {
            if (bytes.length == MAX_BYTES)
                throw new IndexOutOfBoundsException();
            
            bytes = Arrays.copyOf(bytes, Math.min(bytes.length + (bytes.length >> 2) + 4, MAX_BYTES));
        }
    }
    
    @Override
    public void write(int b) {
        try {
            tryResize(pointer + 1);
            bytes[pointer++] = (byte)b;
            
        } catch (IndexOutOfBoundsException e) {
            logger.log(Level.WARNING, "LoggerOutputStream.write.Overflow");
            flush();
            
            bytes = new byte[] { (byte)b };
            pointer = 1;
        }
    }
    
    @Override
    public void write(byte[] b) {
        write0(b, 0, b.length);
    }
    
    @Override
    public void write(byte[] b, int off, int len) {
        if (b == null)
            throw new NullPointerException();
        if (off < 0 || len < 0)
            throw new IndexOutOfBoundsException();
        
        write0(b, off, off + len);
    }
    
    private void write0(byte[] b, int start, int end) {
        boolean doContinue = true;
        
        while (doContinue) {
            try {
                tryResize(pointer + end - start);
                
                while (start < end)
                    bytes[pointer++] = b[start++];
                doContinue = false;

            } catch (IndexOutOfBoundsException e) {
                logger.log(Level.WARNING, "LoggerOutputStream.write.Overflow");

                while (pointer < MAX_BYTES)
                    bytes[pointer++] = b[start++];
                flush();
            }
        }
    }

    @Override
    public void flush() {
        if (bytes.length > 0) {
            logger.log(level, new String(Arrays.copyOfRange(bytes, 0, pointer)));
            bytes = new byte[0];
            pointer = 0;
        }
    }
}
