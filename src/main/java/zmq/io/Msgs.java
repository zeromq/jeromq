package zmq.io;

import zmq.Msg;

public class Msgs
{
    private Msgs()
    {
        // no possible instantiation
    }

    /**
     * Checks if the message starts with the given string.
     *
     * @param msg the message to check.
     * @param data the string to check the message with. Shall be shorter than 256 characters.
     * @param includeLength true if the string in the message is prefixed with the length, false if not.
     * @return true if the message starts with the given string, otherwise false.
     */
    public static boolean startsWith(Msg msg, String data, boolean includeLength)
    {
        final int length = data.length();
        assert (length < 256);

        int start = includeLength ? 1 : 0;
        if (msg.size() < length + start) {
            return false;
        }
        boolean comparison = includeLength ? length == (msg.get(0) & 0xff) : true;
        if (comparison) {
            for (int idx = start; idx < length; ++idx) {
                comparison = (msg.get(idx) == data.charAt(idx - start));
                if (!comparison) {
                    break;
                }
            }
        }
        return comparison;
    }
}
