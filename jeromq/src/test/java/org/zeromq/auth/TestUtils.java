package org.zeromq.auth;

import java.io.File;

public class TestUtils
{
    private TestUtils()
    {
    }

    /**
     * Remove a directory and delete all files and sub dirs recursively
     * CAUTION: beware of symbolic links. not tested how the behavior is but suspect this function will follow those as well.
     * @param path as String
     */
    public static void cleanupDir(String path)
    {
        cleanupDir(new File(path));
    }

    private static void cleanupDir(File path)
    {
        if (!path.exists()) {
            return;
        }

        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                cleanupDir(file);
                file.delete();
            }
        }
        path.delete();
    }
}
