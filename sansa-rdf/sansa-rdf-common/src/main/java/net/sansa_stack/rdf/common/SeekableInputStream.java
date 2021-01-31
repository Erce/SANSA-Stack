package net.sansa_stack.rdf.common;

import org.apache.commons.io.input.ProxyInputStream;
import org.apache.hadoop.fs.Seekable;

import java.io.InputStream;

/** Combine Seekable and InputStream into one class */
public class SeekableInputStream
    extends ProxyInputStream implements SeekableBase
{
    protected Seekable seekable;

    /**
     * Constructs a new ProxyInputStream.
     *
     * @param proxy the InputStream to delegate to
     */
    public SeekableInputStream(InputStream proxy, Seekable seekable) {
        super(proxy);
        this.seekable = seekable;
    }

    @Override
    public Seekable getSeekable() {
        return seekable;
    }
}