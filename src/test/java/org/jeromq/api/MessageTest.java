package org.jeromq.api;

import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class MessageTest {

    @Test
    public void testAddFrame() throws Exception {
        Message testClass = new Message();
        testClass.addFrame(new byte[]{5, 6, 7});
        List<Message.Frame> frames = testClass.getFrames();
        assertEquals(1, frames.size());
        assertArrayEquals(new byte[]{5, 6, 7}, frames.get(0).getData());
    }

    @Test
    public void testBlankFrame() throws Exception {
        Message testClass = new Message();
        testClass.addEmptyFrame();
        List<Message.Frame> frames = testClass.getFrames();
        assertEquals(1, frames.size());
        assertArrayEquals(new byte[0], frames.get(0).getData());
    }

    @Test
    public void testMixedFrames() throws Exception {
        Message testClass = new Message();
        testClass.addEmptyFrame();
        testClass.addFrame("Hello");
        List<Message.Frame> frames = testClass.getFrames();
        assertEquals(2, frames.size());
        assertArrayEquals(new byte[0], frames.get(0).getData());
        assertEquals("Hello", frames.get(1).getString());
    }

    @Test
    public void testCopyConstructor() throws Exception {
        Message initial = new Message();
        initial.addFrame("hello");
        initial.addEmptyFrame();
        initial.addFrame("goodbye");

        Message newMessage = new Message(initial);
        assertEquals(initial.getFrames(), newMessage.getFrames());
    }

    @Test
    public void testReplaceLast() throws Exception {
        Message expected = new Message();
        expected.addFrame("hello");
        expected.addEmptyFrame();
        expected.addFrame("lemons");

        Message testClass = new Message();
        testClass.addFrame("hello");
        testClass.addEmptyFrame();
        testClass.addFrame("goodbye");

        testClass.replaceLast("lemons");

        assertEquals(expected.getFrames(), testClass.getFrames());

    }
}
