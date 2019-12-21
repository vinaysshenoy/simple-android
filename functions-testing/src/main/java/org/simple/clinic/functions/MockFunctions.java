package org.simple.clinic.functions;

import org.simple.clinic.functions.mocks.MockFunction0;
import org.simple.clinic.functions.mocks.MockFunction1;
import org.simple.clinic.functions.mocks.MockFunction2;
import org.simple.clinic.functions.mocks.MockFunction3;

public class MockFunctions {

  private MockFunctions() {}

  public static <R> MockFunction0<R> function0(R value) {
    return new MockFunction0<>(() -> value);
  }

  public static <R> MockFunction0<R> function0(Function0<R> function0) {
    return new MockFunction0<>(function0);
  }

  public static <P1, R> MockFunction1<P1, R> function1(R value) {
    return new MockFunction1<>((p1) -> value);
  }

  public static <P1, R> MockFunction1<P1, R> function1(Function1<P1, R> function1) {
    return new MockFunction1<>(function1);
  }

  public static <P1, P2, R> MockFunction2<P1, P2, R> function2(R value) {
    return new MockFunction2<>(((p1, p2) -> value));
  }

  public static <P1, P2, R> MockFunction2<P1, P2, R> function2(Function2<P1, P2, R> function2) {
    return new MockFunction2<>(function2);
  }

  public static <P1, P2, P3, R> MockFunction3<P1, P2, P3, R> function3(Function3<P1, P2, P3, R> function3) {
    return new MockFunction3<>(function3);
  }

  public static <P1, P2, P3, R> MockFunction3<P1, P2, P3, R> function3(R value) {
    return new MockFunction3<>((p1, p2, p3) -> value);
  }
}
