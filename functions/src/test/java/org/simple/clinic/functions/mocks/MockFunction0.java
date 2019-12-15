package org.simple.clinic.functions.mocks;

import org.simple.clinic.functions.Function0;
import org.simple.clinic.functions.InvocationsRecorder;

public class MockFunction0<R> implements Function0<R> {

  private final Function0<R> function0;

  public final InvocationsRecorder invocations = new InvocationsRecorder();

  public MockFunction0(Function0<R> function) {
    this.function0 = function;
  }

  @Override
  public R call() {
    invocations.record();
    return function0.call();
  }
}
