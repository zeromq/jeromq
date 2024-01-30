package org.zeromq.auth;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.TemporaryFolderFinder;
import org.zeromq.ZConfig;

public class ZConfigTest
{
    private static final String TEST_FOLDER = "target/testCertFolder";
    private static final ZConfig      conf        = new ZConfig("root", null);

    private String  testFolder = TEST_FOLDER;

    @Before
    public void init() throws IOException
    {
        // create test-passwords
        testFolder = TemporaryFolderFinder.resolve(TEST_FOLDER);
        conf.putValue("/curve/public-key", "abcdefg");
        conf.putValue("/curve/secret-key", "(w3lSF/5yv&j*c&0h{4JHe(CETJSksTr.QSjcZE}");
        conf.putValue("metadata/name", "key-value tests");

        // create test-file with values that should be compatible but are actually not created with this implementation
        File dir = new File(testFolder);
        if (!dir.exists()) {
            dir.mkdir();
        }
        FileWriter write = new FileWriter(testFolder + "/test.zpl");
        write.write("1. ZPL configuration file example\n"); // should be discarded
        write.write(" # some initial comment \n"); // should be discarded
        write.write("meta\n");
        write.write("    leadingquote = \"abcde\n");
        write.write("    endingquote = abcde\"\n");
        write.write("    quoted = \"abcde\"\n");
        write.write("    singlequoted = 'abcde'\n");
        write.write("    bind = tcp://eth0:5555\n");
        write.write("    verbose = 1      #   Ask for a trace\n");
        write.write("    sub # some comment after container-name\n");
        write.write("        fortuna = f95\n");
        write.close();

        write = new FileWriter(testFolder + "/reference.zpl");
        write.write("context\n");
        write.write("    iothreads = 1\n");
        write.write("    verbose = 1      #   Ask for a trace\n");
        write.write("main\n");
        write.write("    type = zqueue    #  ZMQ_DEVICE type\n");
        write.write("    frontend\n");
        write.write("        option\n");
        write.write("            hwm = 1000\n");
        write.write("            swap = 25000000     #  25MB\n");
        write.write("        bind = 'inproc://addr1'\n");
        write.write("        bind = 'ipc://addr2'\n");
        write.write("    backend\n");
        write.write("        bind = inproc://addr3\n");
        write.close();
    }

    @Test
    public void testPutKeyDoubleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("inproc://test", "one");
        assertThat(config.pathExists("inproc://test"), is(true));
    }

    @Test
    public void testPutKeySingleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("server/timeout", "1000");
        assertThat(config.pathExists("server/timeout"), is(true));
    }

    @Test
    public void testGetKeySingleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("server/timeout", "1000");
        Map<String, String> values = config.getValues();
        assertThat(values.toString(), values.size(), is(1));
        assertThat(values.toString(), values.containsKey("server/timeout"), is(true));
    }

    @Test
    @Ignore
    public void testGetKeyDoubleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("inproc://test", "one");
        Map<String, String> values = config.getValues();
        assertThat(values.toString(), values.size(), is(1));
        assertThat(values.toString(), values.containsKey("inproc://test"), is(true));
    }

    @Test
    public void testPutGet()
    {
        assertThat(conf.getValue("/curve/public-key"), is("abcdefg"));
        // intentionally checking without leading /
        assertThat(conf.getValue("curve/secret-key"), is("(w3lSF/5yv&j*c&0h{4JHe(CETJSksTr.QSjcZE}"));
        assertThat(conf.getValue("/metadata/name"), is("key-value tests"));

        // checking default value
        assertThat(conf.getValue("/metadata/nothinghere", "default"), is("default"));
    }

    @Test
    public void testLoadSave() throws IOException
    {
        conf.save(testFolder + "/test.cert");
        assertThat(isFileInPath(testFolder, "test.cert"), is(true));
        ZConfig loadedConfig = ZConfig.load(testFolder + "/test.cert");
        //        Object obj = loadedConfig.getValue("/curve/public-key");
        assertThat(loadedConfig.getValue("/curve/public-key"), is("abcdefg"));
        // intentionally checking without leading /
        assertThat(loadedConfig.getValue("curve/secret-key"), is("(w3lSF/5yv&j*c&0h{4JHe(CETJSksTr.QSjcZE}"));
        assertThat(loadedConfig.getValue("/metadata/name"), is("key-value tests"));
    }

    private boolean isFileInPath(String path, String filename)
    {
        File dir = new File(path);
        if (!dir.isDirectory()) {
            return false;
        }
        for (File file : dir.listFiles()) {
            if (file.getName().equals(filename)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testZPLSpecialCases() throws IOException
    {
        // this file was generated in the init-method and tests some cases that should be processed by the loader but are not
        // created with our writer.
        ZConfig zplSpecials = ZConfig.load(testFolder + "/test.zpl");
        // test leading quotes
        assertThat(zplSpecials.getValue("meta/leadingquote"), is("\"abcde"));
        // test ending quotes
        assertThat(zplSpecials.getValue("meta/endingquote"), is("abcde\""));
        // test full doublequoted. here the quotes should be removed
        assertThat(zplSpecials.getValue("meta/quoted"), is("abcde"));
        // test full singlequoted. here the quotes should be removed
        assertThat(zplSpecials.getValue("meta/singlequoted"), is("abcde"));
        // test no quotes tcp-pattern
        assertThat(zplSpecials.getValue("meta/bind"), is("tcp://eth0:5555"));
        // test comment after value
        assertThat(zplSpecials.getValue("meta/verbose"), is("1"));
        // test comment after container-name
        assertThat(zplSpecials.pathExists("meta/sub"), is(true));
    }

    @Test
    public void testReadReference() throws IOException
    {
        ZConfig ref = ZConfig.load(testFolder + "/reference.zpl");
        assertThat(ref.getValue("context/iothreads"), is("1"));
        assertThat(ref.getValue("context/verbose"), is("1"));
        assertThat(ref.getValue("main/type"), is("zqueue"));
        assertThat(ref.getValue("main/frontend/option/hwm"), is("1000"));
        assertThat(ref.getValue("main/frontend/option/swap"), is("25000000"));
        assertThat(ref.getValue("main/frontend/bind"), is("ipc://addr2"));
        assertThat(ref.getValue("main/backend/bind"), is("inproc://addr3"));
    }

    @After
    public void cleanup()
    {
        TestUtils.cleanupDir(testFolder);
    }
}
