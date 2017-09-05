package org.zeromq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets applications load, work with, and save configuration files.
 * This is a minimal implementation of the <a href="https://rfc.zeromq.org/spec:4/ZPL/">ZeroMQ Property Language</a>,
 * which is a simple structured text format for configuration files.
 * <p>
 * Here is an example ZPL stream and corresponding config structure:
 * <p>
 * <pre>
 * {@code
        context
            iothreads = 1
            verbose = 1      #   Ask for a trace
        main
            type = zqueue    #  ZMQ_DEVICE type
            frontend
                option
                    hwm = 1000
                    swap = 25000000     #  25MB
                bind = 'inproc://addr1'
                bind = 'ipc://addr2'
            backend
                bind = inproc://addr3
   }
   <p>
   {@code
    root                    Down = child
    |                     Across = next
    v
    context-->main
    |         |
    |         v
    |       type=queue-->frontend-->backend
    |                      |          |
    |                      |          v
    |                      |        bind=inproc://addr3
    |                      v
    |                    option-->bind=inproc://addr1-->bind=ipc://addr2
    |                      |
    |                      v
    |                    hwm=1000-->swap=25000000
    v
    iothreads=1-->verbose=false
 }
 </pre>
 *
 * It can put and get values and save and load them to disk:
 * <p>
 * <pre>
 * {@code
 * ZConfig conf = new ZConfig("root", null);
 * conf.put("/curve/public-key","abcdef");
 * String val = conf.get("/curve/public-key","fallback-defaultkey");
 * conf.save("test.cert");
 * ZConfig loaded = ZConfig.load("test.cert");
 }
 */
public class ZConfig
{
    private interface IVisitor
    {
        void handleNode(ZConfig node, int level) throws IOException;
    }

    private static final String  LEFT           = "^( *)([0-9a-zA-Z\\$\\-_@\\.&\\+\\/]+)";
    private static final Pattern PTRN_CONTAINER = Pattern.compile(LEFT + "( *#.*)?$");
    private static final Pattern PTRN_KEYVALUE  = Pattern.compile(LEFT + " = ((\"|')(.*)(\\4)|(.*?))(#.*)?$");

    private final String               name;
    private final Map<String, ZConfig> children = new HashMap<>();
    private final List<String>         comments = new LinkedList<>();

    private String value;

    public ZConfig(String name, ZConfig parent)
    {
        this.name = name;
        if (parent != null) {
            parent.children.put(name, this);
        }
    }

    public ZConfig getChild(String name)
    {
        return children.get(name);
    }

    public Map<String, String> getValues()
    {
        Map<String, String> values = new HashMap<>();
        fillValues("", values);
        return values;
    }

    private void fillValues(String prefix, Map<String, String> values)
    {
        for (Entry<String, ZConfig> entry : children.entrySet()) {
            String key = entry.getKey();
            ZConfig child = entry.getValue();
            assert (child != null);
            if (child.value != null) {
                values.put(prefix + key, child.value);
            }
            child.fillValues(prefix + key + '/', values);
        }
    }

    public String getName()
    {
        return this.name;
    }

    public String getValue(String path)
    {
        return getValue(path, null);
    }

    public String getValue(String path, String defaultValue)
    {
        String[] pathElements = path.split("/");
        ZConfig current = this;
        for (String pathElem : pathElements) {
            if (pathElem.isEmpty()) {
                continue;
            }
            current = current.children.get(pathElem);
            if (current == null) {
                return defaultValue;
            }
        }
        return current.value;
    }

    /**
     * check if a value-path exists
     */
    public boolean pathExists(String path)
    {
        String[] pathElements = path.split("/");
        ZConfig current = this;
        for (String pathElem : pathElements) {
            if (pathElem.isEmpty()) {
                continue;
            }
            current = current.children.get(pathElem);
            if (current == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * add comment
     */
    public void addComment(String comment)
    {
        comments.add(comment);
    }

    /**
     * @param value set value of config item
     */
    public ZConfig putValue(String path, String value)
    {
        String[] pathElements = path.split("/");
        ZConfig current = this;
        for (String pathElement : pathElements) {
            if (pathElement.isEmpty()) {
                // ignore leading slashes
                continue;
            }
            ZConfig container = current.children.get(pathElement);
            if (container == null) {
                container = new ZConfig(pathElement, current);
            }
            current = container;
        }
        current.value = value;
        return current;
    }

    private void visit(ZConfig startNode, IVisitor handler, int level) throws IOException
    {
        handler.handleNode(startNode, level);
        for (ZConfig node : startNode.children.values()) {
            visit(node, handler, level + 1);
        }
    }

    public File save(String filename) throws IOException
    {
        if (filename.equals("-")) {
            // print to console
            Writer writer = new PrintWriter(System.out);
            try {
                save(writer);
            }
            finally {
                writer.close();
            }
            return null;
        }
        else { // write to file
            final File file = new File(filename);
            if (file.exists()) {
                file.delete();
            }
            else {
                // create necessary directories;
                file.getParentFile().mkdirs();
            }
            Writer writer = new FileWriter(file);
            try {
                save(writer);
            }
            finally {
                writer.close();
            }
            return file;
        }
    }

    public void save(final Writer writer) throws IOException
    {
        visit(this, new IVisitor()
        {
            @Override
            public void handleNode(ZConfig node, int level) throws IOException
            {
                // First print comments
                if (node.comments.size() > 0) {
                    for (String comment : node.comments) {
                        writer.append("# ").append(comment).append('\n');
                    }
                    writer.append("\n");
                }
                // now the values
                if (level > 0) {
                    String prefix = level > 1 ? String.format("%" + ((level - 1) * 4) + "s", " ") : "";
                    writer.append(prefix);
                    if (node.value == null) {
                        writer.append(node.name).append("\n");
                    }
                    else {
                        writer.append(String.format("%s = \"%s\"\n", node.name, node.value));
                    }
                }
            }
        }, 0);
    }

    public static ZConfig load(String filename) throws IOException
    {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        try {
            List<String> content = new ArrayList<>();

            String line = reader.readLine();

            while (line != null) {
                boolean irrelevant = line.matches("^ *#.*|^ *[0-9]+.*") // ignore comments
                        || line.trim().isEmpty(); // ignore empty lines;
                if (!irrelevant) {
                    content.add(line);
                }

                line = reader.readLine();
            }

            return load(new ZConfig("root", null), content, 0, new AtomicInteger());
        }
        finally {
            reader.close();
        }
    }

    private static ZConfig load(ZConfig parent, List<String> content, int currentLevel, AtomicInteger lineNumber)
    {
        while (lineNumber.get() < content.size()) {
            String currentLine = content.get(lineNumber.get());

            Matcher container = PTRN_CONTAINER.matcher(currentLine);
            if (container.find()) {
                ZConfig child = child(parent, container, currentLevel, currentLine, lineNumber);
                if (child == null) {
                    // jump back;
                    break;
                }
                load(child, content, currentLevel + 1, lineNumber);
            }
            else {
                Matcher keyvalue = PTRN_KEYVALUE.matcher(currentLine);
                if (keyvalue.find()) {
                    ZConfig child = child(parent, keyvalue, currentLevel, currentLine, lineNumber);
                    if (child == null) {
                        // jump back;
                        break;
                    }
                    String value = keyvalue.group(5);
                    if (value == null) {
                        value = keyvalue.group(7);
                    }

                    if (value != null) {
                        value = value.trim();
                    }

                    child.value = value;
                }
                else {
                    throw new ReadException("Couldn't process line", currentLine, lineNumber);
                }
            }
        }
        return parent;
    }

    private static ZConfig child(ZConfig parent, Matcher matcher, int currentLevel, String currentLine,
                                 AtomicInteger lineNumber)
    {
        int level = matcher.group(1).length() / 4;

        if (level > currentLevel) {
            throw new ReadException("Level mismatch in line", currentLine, lineNumber);
        }
        else if (level < currentLevel) {
            // jump back;
            return null;
        }
        lineNumber.incrementAndGet();
        return new ZConfig(matcher.group(2), parent);
    }

    public static class ReadException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public final int    currentLineNumber;
        public final String currentLine;

        public ReadException(String message, String currentLine, AtomicInteger currentLineNumber)
        {
            super(String.format("%s %s: %s", message, currentLineNumber, currentLine));
            this.currentLine = currentLine;
            this.currentLineNumber = currentLineNumber.get();
        }
    }
}
