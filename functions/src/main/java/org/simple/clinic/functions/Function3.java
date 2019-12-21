package org.simple.clinic.functions;

public interface Function3<P1, P2, P3, R> {
  R call(P1 p1, P2 p2, P3 p3);
}
