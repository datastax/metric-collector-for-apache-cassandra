package com.datastax.mcac.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.FileFilterUtils;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsightsTestUtil
{
    private static final Logger logger = LoggerFactory.getLogger(InsightsTestUtil.class);

    public static int checkInsightLogFor(File dataDir, String entry) throws IOException
    {
        int numFound = 0;
        Assert.assertTrue(dataDir.getCanonicalPath(), dataDir.isDirectory());
        for (File file : dataDir.listFiles())
        {
            InputStream reader = null;
            try
            {
                if (file.getName().endsWith(".gz"))
                {
                    reader = new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))));
                }
                else
                {
                    reader = new BufferedInputStream(new FileInputStream(file));
                }

                byte[] bytes = new byte[Ints.BYTES];

                while (reader.read(bytes) == bytes.length)
                {
                    int len = readUnsignedInt(bytes);
                    Assert.assertTrue("" + len, len >= 0 && len < 1 << 20);
                    byte[] msg = new byte[len];
                    int read = reader.read(msg);

                    Assert.assertTrue("Message not at encoded length " + len + " vs " + read + " file="+file, read == len);

                    String str = new String(msg);

                    //Found it
                    if (str.contains(entry))
                    {
                        ++numFound;
                        System.err.println(str);
                    }
                }
            }
            finally
            {
                if (reader != null)
                    reader.close();
            }
        }

        System.err.println("Found " + numFound + " instances of " + entry);
        return numFound;
    }

    static int readUnsignedInt(byte[] bytes)
    {
        long unsigned = ( ((bytes[3] & 0xffL) << 24)
                | ((bytes[2] & 0xffL) << 16)
                | ((bytes[1] & 0xffL) << 8)
                |  (bytes[0] & 0xffL));

        //In this case we should never have anything > MAX_INT
        return Ints.checkedCast(unsigned);
    }
}
