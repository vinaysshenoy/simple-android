package org.simple.clinic.functions;

@SuppressWarnings("unused")
public class SynchronisedFunction0<R> implements Function0<R> {

  private final Function0<R> wrapped;

  public SynchronisedFunction0(Function0<R> wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public R call() {
    synchronized (this) {
      return wrapped.call();
    }
  }

  public static <R> Function0<R> synchronised(Function0<R> function0) {
    return new SynchronisedFunction0<>(function0);
  }
}
