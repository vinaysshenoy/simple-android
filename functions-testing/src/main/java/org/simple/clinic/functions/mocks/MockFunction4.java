package org.simple.clinic.functions.mocks;

import org.simple.clinic.functions.Function4;
import org.simple.clinic.functions.InvocationsRecorder;

public class MockFunction4<P1, P2, P3, P4, R> implements Function4<P1, P2, P3, P4, R> {

  private final Function4<P1, P2, P3, P4, R> function4;

  public final InvocationsRecorder invocations = new InvocationsRecorder();

  public MockFunction4(Function4<P1, P2, P3, P4, R> function4) {
    this.function4 = function4;
  }

  @Override
  public R call(P1 p1, P2 p2, P3 p3, P4 p4) {
    invocations.record(p1, p2, p3, p4);
    return function4.call(p1, p2, p3, p4);
  }
}
