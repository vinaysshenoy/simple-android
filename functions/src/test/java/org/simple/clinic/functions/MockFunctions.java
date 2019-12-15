package org.simple.clinic.functions;

import org.simple.clinic.functions.mocks.MockFunction0;
import org.simple.clinic.functions.mocks.MockFunction1;

public class MockFunctions {

  private MockFunctions() {}

  public static <R> MockFunction0<R> function0(R value) {
    return new MockFunction0<>(() -> value);
  }

  public static <R> MockFunction0<R> function0(Function0<R> function0) {
    return new MockFunction0<>(function0);
  }

  public static <P1, R> MockFunction1<P1, R> function1(R value) {
    return new MockFunction1<>((ignored) -> value);
  }

  public static <P1, R> MockFunction1<P1, R> function1(Function1<P1, R> function1) {
    return new MockFunction1<>(function1);
  }
}
