package org.simple.clinic.functions.mocks;

import org.simple.clinic.functions.Function1;
import org.simple.clinic.functions.InvocationsRecorder;

public class MockFunction1<P1, R> implements Function1<P1, R> {

  private final Function1<P1, R> function1;

  public final InvocationsRecorder invocations = new InvocationsRecorder();

  public MockFunction1(Function1<P1, R> function1) {
    this.function1 = function1;
  }

  @Override
  public R call(P1 p1) {
    invocations.record(p1);
    return function1.call(p1);
  }
}
