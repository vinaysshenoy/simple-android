package org.simple.clinic.functions.mocks;

import org.simple.clinic.functions.Function3;
import org.simple.clinic.functions.InvocationsRecorder;

public class MockFunction3<P1, P2, P3, R> implements Function3<P1, P2, P3, R> {

  private final Function3<P1, P2, P3, R> function3;

  public final InvocationsRecorder invocations = new InvocationsRecorder();

  public MockFunction3(Function3<P1, P2, P3, R> function3) {
    this.function3 = function3;
  }

  @Override
  public R call(P1 p1, P2 p2, P3 p3) {
    invocations.record(p1, p2, p3);
    return function3.call(p1, p2, p3);
  }
}
