package org.zeromq;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;

public class TemporaryFolderFinder {

    public static String resolve(String file)
    {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new File(appContext.getCacheDir(), file).getAbsolutePath();
    }
}
