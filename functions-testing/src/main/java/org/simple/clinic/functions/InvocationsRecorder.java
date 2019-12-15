package org.simple.clinic.functions;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvocationsRecorder {

  private final List<Object[]> invocations = new ArrayList<>();

  public void record(Object... parameters) {
    invocations.add(parameters);
  }

  public InvocationsRecorder assertNumberOfCalls(int callCount) {
    final int actualNumberOfCalls = invocations.size();
    if (actualNumberOfCalls != callCount) {
      fail(format("Expected to be invoked [%d] number of times, but was invoked [%d] times", callCount, actualNumberOfCalls));
    }

    return this;
  }

  public InvocationsRecorder assertCalled() {
    return assertNumberOfCalls(1);
  }

  public InvocationsRecorder assertNeverCalled() {
    return assertNumberOfCalls(0);
  }

  public InvocationsRecorder assertCall(int callIndex, Object... expectedParameters) {
    assertNumberOfCalls(callIndex + 1);

    Object[] invokedParameters = invocations.get(callIndex);

    checkParameterCounts(callIndex, invokedParameters, expectedParameters);

    for (int i = 0; i < expectedParameters.length; i++) {
      String parameterName = format(Locale.ROOT, "p%d", i + 1);
      checkParameters(parameterName, invokedParameters[i], expectedParameters[i]);
    }

    return this;
  }

  public InvocationsRecorder assertCalledWithParameters(Object... expectedParameters) {
    return this.assertCall(0, expectedParameters);
  }

  private void checkParameters(String parameterName, Object actualParameter, Object expectedParameter) {
    String message = null;

    if (expectedParameter != null && actualParameter != null) {
      if (!expectedParameter.equals(actualParameter)) {
        message = format("Expected parameter [%s] to be [%s], but was [%s]", parameterName, expectedParameter, actualParameter);
      }
    } else {
      if (expectedParameter == null && actualParameter != null) {
        message = format("Expected parameter [%s] to be [null], but it was [%s]", parameterName, actualParameter);
      } else if (expectedParameter != null) {
        message = format("Expected parameter [%s] to be [%s], but it was [null]", parameterName, expectedParameter);
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
