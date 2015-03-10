package timber.log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.robolectric.shadows.ShadowLog.LogItem;

@RunWith(RobolectricTestRunner.class) //
@Config(manifest = Config.NONE)
public class LogTest {
  @Before @After public void setUpAndTearDown() {
    Log.FOREST.clear();
    Log.TAGGED_TREES.clear();
  }

  @Test public void recursion() {
    Log.Tree timber = Log.asTree();
    try {
      Log.plant(timber);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Cannot plant Timber into itself.");
    }
  }

  @Test public void nullTree() {
    try {
      Log.plant(null);
      fail();
    } catch (NullPointerException e) {
      assertThat(e).hasMessage("tree == null");
    }
  }

  @Test public void uprootThrowsIfMissing() {
    try {
      Log.uproot(new Log.DebugTree());
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageStartingWith("Cannot uproot tree which is not planted: ");
    }
  }

  @Test public void uprootRemovesTree() {
    Log.DebugTree tree1 = new Log.DebugTree();
    Log.DebugTree tree2 = new Log.DebugTree();
    Log.plant(tree1);
    Log.plant(tree2);
    Log.d("First");
    Log.uproot(tree1);
    Log.d("Second");

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(3);
    assertThat(logs.get(0).msg).isEqualTo("First");
    assertThat(logs.get(1).msg).isEqualTo("First");
    assertThat(logs.get(2).msg).isEqualTo("Second");
  }

  @Test public void uprootAllRemovesAll() {
    Log.DebugTree tree1 = new Log.DebugTree();
    Log.DebugTree tree2 = new Log.DebugTree();
    Log.plant(tree1);
    Log.plant(tree2);
    Log.d("First");
    Log.uprootAll();
    Log.d("Second");

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(2);
    assertThat(logs.get(0).msg).isEqualTo("First");
    assertThat(logs.get(1).msg).isEqualTo("First");
  }

  @Test public void noArgsDoesNotFormat() {
    Log.plant(new Log.DebugTree());
    Log.d("te%st");

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(1);
    LogItem log = logs.get(0);
    assertThat(log.type).isEqualTo(android.util.Log.DEBUG);
    assertThat(log.tag).isEqualTo("LogTest");
    assertThat(log.msg).isEqualTo("te%st");
    assertThat(log.throwable).isNull();
  }

  @Test public void debugTreeTagGeneration() {
    Log.plant(new Log.DebugTree());
    Log.d("Hello, world!");

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(1);
    LogItem log = logs.get(0);
    assertThat(log.type).isEqualTo(android.util.Log.DEBUG);
    assertThat(log.tag).isEqualTo("LogTest");
    assertThat(log.msg).isEqualTo("Hello, world!");
    assertThat(log.throwable).isNull();
  }

  @Test public void debugTreeCustomTag() {
    Log.plant(new Log.DebugTree());
    Log.tag("Custom").d("Hello, world!");

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(1);
    LogItem log = logs.get(0);
    assertThat(log.type).isEqualTo(android.util.Log.DEBUG);
    assertThat(log.tag).isEqualTo("Custom");
    assertThat(log.msg).isEqualTo("Hello, world!");
    assertThat(log.throwable).isNull();
  }

  @Test public void debugTreeCustomTagCreation() {
    Log.plant(new Log.DebugTree() {
      @Override protected String createTag() {
        return "Override";
      }
    });
    Log.d("Hello, world!");

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(1);
    LogItem log = logs.get(0);
    assertThat(log.type).isEqualTo(android.util.Log.DEBUG);
    assertThat(log.tag).isEqualTo("Override");
    assertThat(log.msg).isEqualTo("Hello, world!");
    assertThat(log.throwable).isNull();
  }

  @Test public void debugTreeCustomTagCreationCanUseNextTag() {
    final AtomicReference<String> nextTagRef = new AtomicReference<String>();
    Log.plant(new Log.DebugTree() {
      @Override protected String createTag() {
        nextTagRef.set(nextTag());
        return "Override";
      }
    });
    Log.tag("Custom").d("Hello, world!");

    assertThat(nextTagRef.get()).isEqualTo("Custom");
  }

  @Test public void messageWithException() {
    Log.plant(new Log.DebugTree());
    NullPointerException datThrowable = new NullPointerException();
    Log.e(datThrowable, "OMFG!");

    assertExceptionLogged("OMFG!", "java.lang.NullPointerException");
  }

  @Test public void exceptionFromSpawnedThread() throws InterruptedException {
    Log.plant(new Log.DebugTree());
    final NullPointerException datThrowable = new NullPointerException();
    final CountDownLatch latch = new CountDownLatch(1);
    new Thread() {
      @Override public void run() {
        Log.e(datThrowable, "OMFG!");
        latch.countDown();
      }
    }.run();
    latch.await();
    assertExceptionLogged("OMFG!", "java.lang.NullPointerException");
  }

  @Test public void nullMessageWithThrowable() {
    Log.plant(new Log.DebugTree());
    final NullPointerException datThrowable = new NullPointerException();
    Log.e(datThrowable, null);

    assertExceptionLogged("", "java.lang.NullPointerException");
  }

  @Test public void chunkAcrossNewlinesAndLimit() {
    Log.plant(new Log.DebugTree());
    Log.d(repeat('a', 3000) + '\n' + repeat('b', 6000) + '\n' + repeat('c', 3000));

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(4);
    assertThat(logs.get(0).msg).isEqualTo(repeat('a', 3000));
    assertThat(logs.get(1).msg).isEqualTo(repeat('b', 4000));
    assertThat(logs.get(2).msg).isEqualTo(repeat('b', 2000));
    assertThat(logs.get(3).msg).isEqualTo(repeat('c', 3000));
  }

  @Test public void nullMessageWithoutThrowable() {
    Log.plant(new Log.DebugTree());
    Log.d(null);

    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(0);
  }

  @Test public void logMessageCallback() {
    final List<String> logs = new ArrayList<String>();
    Log.plant(new Log.DebugTree() {
      @Override protected void logMessage(int priority, String tag, String message) {
        logs.add(priority + " " + tag + " " + message);
      }
    });

    Log.v("Verbose");
    Log.tag("Custom").v("Verbose");
    Log.d("Debug");
    Log.tag("Custom").d("Debug");
    Log.i("Info");
    Log.tag("Custom").i("Info");
    Log.w("Warn");
    Log.tag("Custom").w("Warn");
    Log.e("Error");
    Log.tag("Custom").e("Error");

    assertThat(logs).containsExactly( //
        "2 LogTest Verbose", //
        "2 Custom Verbose", //
        "3 LogTest Debug", //
        "3 Custom Debug", //
        "4 LogTest Info", //
        "4 Custom Info", //
        "5 LogTest Warn", //
        "5 Custom Warn", //
        "6 LogTest Error", //
        "6 Custom Error");
  }

  private static void assertExceptionLogged(String message, String exceptionClassname) {
    List<LogItem> logs = ShadowLog.getLogs();
    assertThat(logs).hasSize(1);
    LogItem log = logs.get(0);
    assertThat(log.type).isEqualTo(android.util.Log.ERROR);
    assertThat(log.tag).isEqualTo("LogTest");
    assertThat(log.msg).startsWith(message);
    assertThat(log.msg).contains(exceptionClassname);
    // We use a low-level primitive that Robolectric doesn't populate.
    assertThat(log.throwable).isNull();
  }

  private static String repeat(char c, int number) {
    char[] data = new char[number];
    Arrays.fill(data, c);
    return new String(data);
  }
}
