package zmq;

import org.junit.Test;

import zmq.util.Utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestRadioDish
{
    @Test
    public void testTcp() throws Exception
    {
        System.out.println("Scenario 1");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a radio
        SocketBase radio = ZMQ.socket(context, ZMQ.ZMQ_RADIO);
        assertThat(radio, notNullValue());

        // bind server
        boolean rc = ZMQ.bind(radio, "tcp://*:" + port);
        assertThat(rc, is(true));

        // create a dish
        SocketBase dish = ZMQ.socket(context, ZMQ.ZMQ_DISH);
        assertThat(dish, notNullValue());

        // Joining
        rc = dish.join("Movies");
        assertThat(rc, is(true));

        // Connecting
        rc = ZMQ.connect(dish, "tcp://localhost:" + port);
        assertThat(rc, is(true));

        ZMQ.msleep(100);

        // This is not going to be sent as dish only subscribe to "Movies"
        Msg msg = new Msg("Friends".getBytes());
        msg.setGroup("TV");
        rc = radio.send(msg, 0);
        assertThat(rc, is(true));

        // This is going to be sent to the dish
        msg = new Msg("Godfather".getBytes());
        msg.setGroup("Movies");
        rc = radio.send(msg, 0);
        assertThat(rc, is(true));

        msg = dish.recv(0);
        assertThat(msg.getGroup(), is("Movies"));
        assertThat(new String(msg.data()), is("Godfather"));

        // Join group during connection
        rc = dish.join("TV");
        assertThat(rc, is(true));

        ZMQ.msleep(100);

        // This should arrive now as we joined the group
        msg = new Msg("Friends".getBytes());
        msg.setGroup("TV");
        rc = radio.send(msg, 0);
        assertThat(rc, is(true));

        // Check the correct message arrived
        msg = dish.recv(0);
        assertThat(msg.getGroup(), is("TV"));
        assertThat(new String(msg.data()), is("Friends"));

        // Leaving group
        rc = dish.leave("TV");
        assertThat(rc, is(true));

        ZMQ.msleep(100);

        // This is not going to be sent as dish only subscribe to "Movies"
        msg = new Msg("Friends".getBytes());
        msg.setGroup("TV");
        rc = radio.send(msg, 0);
        assertThat(rc, is(true));

        //  This is going to be sent to the dish
        msg = new Msg("Godfather".getBytes());
        msg.setGroup("Movies");
        rc = radio.send(msg, 0);
        assertThat(rc, is(true));

        msg = dish.recv(0);
        assertThat(msg.getGroup(), is("Movies"));
        assertThat(new String(msg.data()), is("Godfather"));

        ZMQ.close(dish);
        ZMQ.close(radio);
        ZMQ.term(context);
    }
}
