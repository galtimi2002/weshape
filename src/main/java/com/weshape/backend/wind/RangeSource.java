package com.weshape.backend.wind;

import java.io.IOException;

/** Reads byte ranges of a stored file: the wind layers in the R2 bucket, or local copies. */
public interface RangeSource {

    /** Bytes [offset, offset + length) of the file at key. Throws FileNotFoundException when there is no such file. */
    byte[] read(String key, long offset, int length) throws IOException;

    /** Where the files come from, for logs. */
    String describe();
}
