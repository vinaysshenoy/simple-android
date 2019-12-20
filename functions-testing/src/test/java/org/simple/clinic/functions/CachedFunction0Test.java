package org.simple.clinic.functions;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.simple.clinic.functions.mocks.MockFunction0;

public class CachedFunction0Test {

  @Test
  public void when_invoked_multiple_times_it_should_cached_and_return_the_previously_computed_value() {
    final SomeClass suppliedValue = new SomeClass("first", 0.5F);
    final MockFunction0<SomeClass> originalSupplier = MockFunctions.function0(suppliedValue);
    final CachedFunction0<SomeClass> cachedFunction0 = new CachedFunction0<>(originalSupplier);

    final SomeClass returnedValueFromFirstInvocation = cachedFunction0.call();
    final SomeClass returnedValueFromSecondInvocation = cachedFunction0.call();

    originalSupplier.invocations.assertNumberOfCalls(1);
    assertThat(returnedValueFromFirstInvocation).isSameInstanceAs(suppliedValue);
    assertThat(returnedValueFromFirstInvocation).isSameInstanceAs(returnedValueFromSecondInvocation);
  }

  @Test
  public void null_values_should_not_be_treated_as_a_signal_for_not_computed_values() {
    final MockFunction0<SomeClass> originalSupplier = MockFunctions.function0(() -> null);
    final CachedFunction0<SomeClass> cachedFunction0 = new CachedFunction0<>(originalSupplier);

    cachedFunction0.call();
    cachedFunction0.call();

    originalSupplier.invocations.assertNumberOfCalls(1);
  }

  private class SomeClass {
    private final String a;
    private final float b;

    SomeClass(String a, float b) {
      this.a = a;
      this.b = b;
    }
  }
}
