package guide;

import org.junit.Test;

public class kvmsgTest
{
   @Test
   public void showKmsgIssueTest()
   {
       kvmsg msg = new kvmsg(0);
       msg.setKey("mykey");
       kvmsg clone = msg.dup();
       clone.setKey(clone.getKey() + "theirkey");
       // This should pass but does not in 0.5.3:  Reported in jeromq issue #940
       assert(clone.getKey().equals("mykeytheirkey"));
   }
}
