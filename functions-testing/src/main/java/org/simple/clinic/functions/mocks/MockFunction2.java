package org.simple.clinic.functions.mocks;

import org.simple.clinic.functions.Function2;
import org.simple.clinic.functions.InvocationsRecorder;

public class MockFunction2<P1, P2, R> implements Function2<P1, P2, R> {

  private final Function2<P1, P2, R> function2;

  private final InvocationsRecorder invocations = new InvocationsRecorder();

  public MockFunction2(Function2<P1, P2, R> function2) {
    this.function2 = function2;
  }

  @Override
  public R call(P1 p1, P2 p2) {
    invocations.record(p1, p2);
    return function2.call(p1, p2);
  }
}
