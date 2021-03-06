/*
 *  Copyright (c) 2020 Applica.ai All Rights Reserved
 *
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package ai.applica.spring.boot.starter.temporal.samples;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import ai.applica.spring.boot.starter.temporal.WorkflowFactory;
import ai.applica.spring.boot.starter.temporal.annotations.TemporalTest;
import ai.applica.spring.boot.starter.temporal.samples.apps.HelloActivityRetry.GreetingActivities;
import ai.applica.spring.boot.starter.temporal.samples.apps.HelloActivityRetry.GreetingWorkflow;
import ai.applica.spring.boot.starter.temporal.samples.apps.HelloActivityRetry.GreetingWorkflowImpl;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/** Unit test for {@link HelloActivityRetry}. Doesn't use an external Temporal service. */
@RunWith(SpringRunner.class)
@SpringBootTest
@TemporalTest
public class HelloActivityRetryTest {

  /** Prints a history of the workflow under test in case of a test failure. */
  @Rule
  public TestWatcher watchman =
      new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
          if (testEnv != null) {
            System.err.println(testEnv.getDiagnostics());
            testEnv.close();
          }
        }
      };

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  GreetingWorkflow workflow;

  @Autowired WorkflowFactory fact;
  @Autowired GreetingActivities greatActivity;

  @Before
  public void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    worker = fact.makeWorker(testEnv, GreetingWorkflowImpl.class);

    // Get a workflow stub using the same task queue the worker uses.
    workflow =
        fact.makeStub(
            GreetingWorkflow.class, GreetingWorkflowImpl.class, testEnv.getWorkflowClient());
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testActivityImpl() {
    worker.registerActivitiesImplementations(greatActivity);
    testEnv.start();

    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    assertEquals("Hello World!", greeting);
  }

  @Test(timeout = 1000)
  public void testMockedActivity() {
    GreetingActivities activities = mock(GreetingActivities.class);
    when(activities.composeGreeting("Hello", "World"))
        .thenThrow(
            new IllegalStateException("not yet1"),
            new IllegalStateException("not yet2"),
            new IllegalStateException("not yet3"))
        .thenReturn("Hello World!");
    worker.registerActivitiesImplementations(activities);
    testEnv.start();

    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    assertEquals("Hello World!", greeting);

    verify(activities, times(4)).composeGreeting(anyString(), anyString());
  }
}
