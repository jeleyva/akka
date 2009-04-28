/**
 * Copyright (C) 2009 Scalable Solutions.
 */

package se.scalablesolutions.akka.api;

import se.scalablesolutions.akka.annotation.*;
import se.scalablesolutions.akka.kernel.*;
import se.scalablesolutions.akka.kernel.configuration.LifeCycle;
import se.scalablesolutions.akka.kernel.configuration.Permanent;
import se.scalablesolutions.akka.kernel.configuration.Component;
import se.scalablesolutions.akka.kernel.configuration.AllForOne;
import se.scalablesolutions.akka.kernel.configuration.RestartStrategy;

import com.google.inject.Inject;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import junit.framework.TestCase;

public class InMemoryStateTest extends TestCase {
  static String messageLog = "";

  final private ActiveObjectGuiceConfigurator conf = new ActiveObjectGuiceConfigurator();

  protected void setUp() {
    conf.configureActiveObjects(
        new RestartStrategy(new AllForOne(), 3, 5000),
        new Component[] { 
          new Component(InMemStateful.class, InMemStatefulImpl.class, new LifeCycle(new Permanent(), 1000), 10000000), 
          new Component(InMemFailer.class, InMemFailerImpl.class, new LifeCycle(new Permanent(), 1000), 1000),
          new Component(InMemClasher.class, InMemClasherImpl.class, new LifeCycle(new Permanent(), 1000), 100000) 
        }).inject().supervise();

  }

  // public void testShouldNotRollbackStateForStatefulServerInCaseOfSuccess() {
  // Stateful stateful = conf.getActiveObject(Stateful.class);
  // stateful.setState("stateful", "init"); // set init state
  // stateful.success("stateful", "new state"); // transactional
  // assertEquals("new state", stateful.getState("stateful"));
  // }
  //
  // public void testShouldRollbackStateForStatefulServerInCaseOfFailure() {
  // Stateful stateful = conf.getActiveObject(Stateful.class);
  // stateful.setState("stateful", "init"); // set init state
  //
  // Failer failer = conf.getActiveObject(Failer.class);
  // try {
  // stateful.failure("stateful", "new state", failer); // call failing
  // transactional method
  // fail("should have thrown an exception");
  // } catch (RuntimeException e) { } // expected
  // assertEquals("init", stateful.getState("stateful")); // check that state is
  // == init state
  // }

  public void testShouldRollbackStateForStatefulServerInCaseOfMessageClash() {
    InMemStateful stateful = conf.getActiveObject(InMemStateful.class);
    stateful.setState("stateful", "init"); // set init state

    InMemClasher clasher = conf.getActiveObject(InMemClasher.class);
    clasher.setState("clasher", "init"); // set init state

    // try {
    // stateful.clashOk("stateful", "new state", clasher);
    // } catch (RuntimeException e) { } // expected
    // assertEquals("new state", stateful.getState("stateful")); // check that
    // state is == init state
    // assertEquals("was here", clasher.getState("clasher")); // check that
    // state is == init state

    try {
      stateful.clashNotOk("stateful", "new state", clasher);
      fail("should have thrown an exception");
    } catch (RuntimeException e) {
      System.out.println(e);
    } // expected
    assertEquals("init", stateful.getState("stateful")); // check that state is
    // == init state
    // assertEquals("init", clasher.getState("clasher")); // check that state is
    // == init state
  }
}

interface InMemStateful {
  // transactional
  @transactional
  public void success(String key, String msg);

  @transactional
  public void failure(String key, String msg, InMemFailer failer);

  @transactional
  public void clashOk(String key, String msg, InMemClasher clasher);

  @transactional
  public void clashNotOk(String key, String msg, InMemClasher clasher);

  // non-transactional
  public String getState(String key);

  public void setState(String key, String value);
}

class InMemStatefulImpl implements InMemStateful {
  @state
  private TransactionalMap<String, Object> state = new InMemoryTransactionalMap<String, Object>();

  public String getState(String key) {
    return (String) state.get(key);
  }

  public void setState(String key, String msg) {
    state.put(key, msg);
  }

  public void success(String key, String msg) {
    state.put(key, msg);
  }

  public void failure(String key, String msg, InMemFailer failer) {
    state.put(key, msg);
    failer.fail();
  }

  public void clashOk(String key, String msg, InMemClasher clasher) {
    state.put(key, msg);
    clasher.clash();
  }

  public void clashNotOk(String key, String msg, InMemClasher clasher) {
    state.put(key, msg);
    clasher.clash();
    clasher.clash();
  }
}

interface InMemFailer {
  public void fail();
}

class InMemFailerImpl implements InMemFailer {
  public void fail() {
    throw new RuntimeException("expected");
  }
}

interface InMemClasher {
  public void clash();

  public String getState(String key);

  public void setState(String key, String value);
}

class InMemClasherImpl implements InMemClasher {
  @state
  private TransactionalMap<String, Object> state = new InMemoryTransactionalMap<String, Object>();

  public String getState(String key) {
    return (String) state.get(key);
  }

  public void setState(String key, String msg) {
    state.put(key, msg);
  }

  public void clash() {
    state.put("clasher", "was here");
    // spend some time here
    for (long i = 0; i < 1000000000; i++) {
      for (long j = 0; j < 10000000; j++) {
        j += i;
      }
    }

    // FIXME: this statement gives me this error:
    // se.scalablesolutions.akka.kernel.ActiveObjectException:
    // Unexpected message [!(scala.actors.Channel@c2b2f6,ErrRef[Right(null)])]
    // to
    // [GenericServer[se.scalablesolutions.akka.api.StatefulImpl]] from
    // [GenericServer[se.scalablesolutions.akka.api.ClasherImpl]]]
    // try { Thread.sleep(1000); } catch (InterruptedException e) {}
  }
}
