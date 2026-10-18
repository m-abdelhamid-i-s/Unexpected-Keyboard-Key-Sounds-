package android.os;

import java.util.LinkedHashMap;
import java.util.Map;

/** JVM-only scheduler for pointer tests. Time advances only when a test asks;
    no Android looper, sleeps or changes to the production repeat scheduler. */
public class Handler
{
  public interface Callback { boolean handleMessage(Message message); }

  private final Callback callback;
  private final Map<Integer, Long> pending = new LinkedHashMap<Integer, Long>();
  public static Handler lastCreated;

  public Handler(Callback c)
  {
    callback = c;
    lastCreated = this;
  }

  public boolean sendEmptyMessageDelayed(int what, long delay)
  {
    pending.put(what, delay);
    return true;
  }

  public void removeMessages(int what) { pending.remove(what); }

  public boolean fireNext()
  {
    if (pending.isEmpty())
      return false;
    int what = pending.keySet().iterator().next();
    pending.remove(what);
    Message message = new Message();
    message.what = what;
    return callback.handleMessage(message);
  }
}
