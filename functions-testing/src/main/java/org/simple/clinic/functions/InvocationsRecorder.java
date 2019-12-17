package org.simple.clinic.functions;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvocationsRecorder {

  private final Locale messageLocale;

  private final List<Object[]> invocations = new ArrayList<>();

  public InvocationsRecorder() {
    this(Locale.ENGLISH);
  }

  @SuppressWarnings("WeakerAccess")
  public InvocationsRecorder(Locale messageLocale) {
    this.messageLocale = messageLocale;
  }

  public void record(Object... parameters) {
    invocations.add(parameters);
  }

  @SuppressWarnings("WeakerAccess")
  public InvocationsRecorder assertNumberOfCalls(int callCount) {
    final int actualNumberOfCalls = invocations.size();
    if (actualNumberOfCalls != callCount) {
      fail(format(messageLocale, "Expected to be invoked exactly [%d] number of times, but was invoked [%d] times", callCount, actualNumberOfCalls));
    }

    return this;
  }

  @SuppressWarnings({ "WeakerAccess", "unused", "UnusedReturnValue" })
  public InvocationsRecorder assertCalled() {
    return assertNumberOfCalls(1);
  }

  @SuppressWarnings("WeakerAccess")
  public InvocationsRecorder assertNeverCalled() {
    return assertNumberOfCalls(0);
  }

  @SuppressWarnings("WeakerAccess")
  public InvocationsRecorder assertCall(int callIndex, Object... expectedParameters) {
    assertCalledAtLeast(callIndex + 1);

    Object[] invokedParameters = invocations.get(callIndex);

    checkParameterCounts(callIndex, invokedParameters, expectedParameters);

    for (int i = 0; i < expectedParameters.length; i++) {
      String parameterName = format(Locale.ROOT, "p%d", i + 1);
      checkParameters(parameterName, invokedParameters[i], expectedParameters[i]);
    }

    return this;
  }

  @SuppressWarnings("WeakerAccess")
  public InvocationsRecorder assertCalledWithParameters(Object... expectedParameters) {
    assertNumberOfCalls(1);
    assertCall(0, expectedParameters);
    return this;
  }

  @SuppressWarnings({ "WeakerAccess", "UnusedReturnValue" })
  public InvocationsRecorder assertCalledAtLeast(int callCount) {
    final int actualNumberOfCalls = invocations.size();
    if (actualNumberOfCalls < callCount) {
      fail(format(messageLocale, "Expected to be invoked at least [%d] number of times, but was invoked [%d] times", callCount, actualNumberOfCalls));
    }

    return this;
  }

  private void checkParameters(String parameterName, Object actualParameter, Object expectedParameter) {
    String message = null;

    if (expectedParameter != null && actualParameter != null) {
      if (!expectedParameter.equals(actualParameter)) {
        message = format(
            messageLocale,
            "Expected parameter [%s] to be [%s], but was [%s]",
            parameterName,
            expectedParameter,
            actualParameter
        );
      }
    } else {
      if (expectedParameter == null && actualParameter != null) {
        message = format(messageLocale, "Expected parameter [%s] to be [null], but it was [%s]", parameterName, actualParameter);
      } else if (expectedParameter != null) {
        message = format(
            messageLocale,
            "Expected parameter [%s] to be [%s], but it was [null]",
            parameterName,
            expectedParameter
        );
      }
    }

    if (message != null) {
      fail(message);
    }
  }

  private void checkParameterCounts(int callIndex, Object[] invokedParameters, Object[] expectedParameters) {
    int numberOfExpectedParameters = expectedParameters.length;
    int numberOfActualParameters = invokedParameters.length;

    if (numberOfActualParameters != numberOfExpectedParameters) {
      String message = format(
          messageLocale,
          "Expected call #%d to be invoked with [%d] parameters, but was actually invoked with [%d] parameters",
          callIndex,
          numberOfExpectedParameters,
          numberOfActualParameters
      );
      fail(message);
    }
  }

  private void fail(String message) {
    throw new AssertionError(message);
  }
}
