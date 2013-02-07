package org.jeromq.api;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class RoutedMessageTest {

    @Test
    public void testGetRoutes() throws Exception {
        RoutedMessage testClass = new RoutedMessage();
        testClass.addFrames(new Message(
                Arrays.asList(new Message.Frame("address1"),
                        RoutedMessage.Route.BLANK,
                        new Message.Frame("payload"))));

        List<RoutedMessage.Route> result = testClass.getRoutes();
        assertEquals(1, result.size());
        assertEquals(new RoutedMessage.Route("address1".getBytes()), result.get(0));
    }

    @Test
    public void testGetRoutes_multiple() throws Exception {
        RoutedMessage testClass = new RoutedMessage();
        testClass.addFrames(new Message(Arrays.asList(
                new Message.Frame("address1"),
                RoutedMessage.Route.BLANK,
                new Message.Frame("address2"),
                RoutedMessage.Route.BLANK,
                new Message.Frame("payload"))));

        List<RoutedMessage.Route> result = testClass.getRoutes();
        assertEquals(2, result.size());
        assertEquals(new RoutedMessage.Route("address1".getBytes()), result.get(0));
        assertEquals(new RoutedMessage.Route("address2".getBytes()), result.get(1));
    }

    @Test
    public void testPayload_multipleRoutes() throws Exception {
        RoutedMessage testClass = new RoutedMessage();
        testClass.addFrames(new Message(Arrays.asList(
                new Message.Frame("address1"),
                RoutedMessage.Route.BLANK,
                new Message.Frame("address2"),
                RoutedMessage.Route.BLANK,
                new Message.Frame("payload"))));

        Message result = testClass.getPayload();
        assertEquals("payload", result.getFirstFrameAsString());
    }

    @Test
    public void testGetRoutes_noRoutes() throws Exception {
        RoutedMessage testClass = new RoutedMessage();
        testClass.addFrame("payload");

        List<RoutedMessage.Route> result = testClass.getRoutes();
        assertTrue(result.isEmpty());
    }

}
