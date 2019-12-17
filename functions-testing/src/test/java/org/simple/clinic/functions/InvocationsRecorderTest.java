package org.simple.clinic.functions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Date;
import java.util.Locale;

public class InvocationsRecorderTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private InvocationsRecorder recorder = new InvocationsRecorder(Locale.ENGLISH);

  @Test
  public void when_never_invoked_asserting_no_calls_were_made_should_not_fail() {
    recorder.assertNeverCalled();
  }

  @Test
  public void when_invoked_once_asserting_no_calls_were_made_should_fail() {
    recorder.record();
    thrown.expect(AssertionError.class);

    recorder.assertNeverCalled();
  }

  @Test
  public void when_invoked_once_asserting_one_call_was_made_should_not_fail() {
    recorder.record();

    recorder.assertNumberOfCalls(1);
  }

  @Test
  public void when_invoked_twice_asserting_one_call_was_made_should_not_fail() {
    recorder.record();
    recorder.record();
    thrown.expect(AssertionError.class);

    recorder.assertCalled();
  }

  @Test
  public void when_invoked_once_asserting_two_calls_were_made_should_fail() {
    recorder.record();
    thrown.expect(AssertionError.class);

    recorder.assertNumberOfCalls(2);
  }

  @Test
  public void when_invoked_once_with_one_parameter_asserting_correct_parameters_should_not_fail() {
    recorder.record(1);

    recorder.assertCalledWithParameters(1);
  }

  @Test
  public void when_invoked_twice_with_one_parameter_asserting_correct_parameters_once_should_fail() {
    recorder.record(1);
    recorder.record(2);
    thrown.expect(AssertionError.class);

    recorder.assertCalledWithParameters(1);
  }

  @Test
  public void when_invoked_multiple_times_with_one_parameter_asserting_correct_parameters_should_not_fail() {
    recorder.record("1");
    recorder.record("2");
    recorder.record("3");

    recorder.assertCall(0, "1");
    recorder.assertCall(1, "2");
    recorder.assertCall(2, "3");
  }

  @Test
  public void when_invoked_once_with_multiple_parameter_asserting_correct_parameters_should_not_fail() {
    SomeClass first = new SomeClass("temp1", 5.0F);
    SomeClass second = new SomeClass("temp2", -5.0F);

    recorder.record(first, SomeEnum.FirstType, new Date(1576549754000L), second);

    recorder.assertCalledWithParameters(first, SomeEnum.FirstType, new Date(1576549754000L), second);
  }

  @Test
  public void when_invoked_multiple_times_with_multiple_parameter_asserting_correct_parameters_should_not_fail() {
    SomeClass first = new SomeClass("temp1", 5.0F);
    SomeClass second = new SomeClass("temp2", -5.0F);
    SomeClass third = new SomeClass("temp3", 0.0F);

    recorder.record(first, SomeEnum.FirstType, new Date(1576549754000L));
    recorder.record(second, SomeEnum.SecondType, new Date(1576549753000L));
    recorder.record(third, SomeEnum.ThirdType, new Date(1576549752000L));

    recorder.assertCall(0, first, SomeEnum.FirstType, new Date(1576549754000L));
    recorder.assertCall(1, second, SomeEnum.SecondType, new Date(1576549753000L));
    recorder.assertCall(2, third, SomeEnum.ThirdType, new Date(1576549752000L));
  }

  @Test
  public void when_invoked_once_with_multiple_parameters_asserting_first_incorrect_parameter_should_fail() {
    recorder.record(1, "1");
    thrown.expect(AssertionError.class);

    recorder.assertCalledWithParameters(2, "1");
  }

  @Test
  public void when_invoked_once_with_multiple_parameters_asserting_second_incorrect_parameter_should_fail() {
    recorder.record(1, "1");
    thrown.expect(AssertionError.class);

    recorder.assertCalledWithParameters(1, "2");
  }

  @Test
  public void when_invoked_multiple_times_with_multiple_parameters_asserting_first_incorrect_invocation_should_fail() {
    SomeClass first = new SomeClass("temp1", 5.0F);
    SomeClass second = new SomeClass("temp2", -5.0F);

    recorder.record(second, SomeEnum.FirstType, new Date(1576549754000L));
    recorder.record(first, SomeEnum.SecondType, new Date(1576549753001L));

    thrown.expect(AssertionError.class);
    recorder.assertCall(0, first, SomeEnum.FirstType, new Date(1576549754000L));
  }

  @Test
  public void when_invoked_multiple_times_with_multiple_parameters_asserting_second_incorrect_invocation_should_fail() {
    SomeClass first = new SomeClass("temp1", 5.0F);
    SomeClass second = new SomeClass("temp2", -5.0F);

    recorder.record(first, SomeEnum.FirstType, new Date(1576549754000L));
    recorder.record(first, SomeEnum.SecondType, new Date(1576549753001L));

    recorder.assertCall(0, first, SomeEnum.FirstType, new Date(1576549754000L));
    thrown.expect(AssertionError.class);
    recorder.assertCall(1, second, SomeEnum.SecondType, new Date(1576549754001L));
  }

  private class SomeClass {
    private final String a;
    private final float b;

    SomeClass(String a, float b) {
      this.a = a;
      this.b = b;
    }
  }

  private enum SomeEnum {
    FirstType, SecondType, ThirdType
  }
}
