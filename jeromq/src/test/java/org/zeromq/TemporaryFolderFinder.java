package org.zeromq;

/**
 * This class is a hacky way of making tests pass on Android devices.
 * Please feel free to amend.
 */
public class TemporaryFolderFinder
{
    private TemporaryFolderFinder()
    {
        // no instantiation
    }

    public static String resolve(String file)
    {
        return file;
    }
}
