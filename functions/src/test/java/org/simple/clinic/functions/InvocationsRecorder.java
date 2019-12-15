package org.simple.clinic.functions;

import java.util.ArrayList;
import java.util.List;

public class InvocationsRecorder {

  private final List<Object[]> invocations = new ArrayList<>();

  public void record(Object... parameters) {
    invocations.add(parameters);
  }
}
