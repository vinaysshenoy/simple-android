package org.simple.clinic.functions;

public class CachedFunction0<R> implements Function0<R> {

  private final Function0<R> wrapped;

  private boolean hasBeenComputed;

  private R cached;

  public CachedFunction0(Function0<R> function0) {
    wrapped = function0;
    hasBeenComputed = false;
    cached = null;
  }

  @Override
  public R call() {
    if (!hasBeenComputed) {
      cached = wrapped.call();
      hasBeenComputed = true;
    }

    return cached;
  }

  public static <R> Function0<R> cached(Function0<R> function0) {
    return new CachedFunction0<>(function0);
  }
}
