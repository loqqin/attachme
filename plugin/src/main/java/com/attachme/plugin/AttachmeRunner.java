package com.attachme.plugin;

import com.attachme.agent.AttachmeServer;
import com.attachme.agent.ProcessRegisterMsg;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AttachmeRunner implements RunProfileState, AttachmeServer.Listener {

  private final Project project;
  private final AttachmeRunConfig runConf;
  private MProcHandler procHandler;

  public AttachmeRunner(AttachmeRunConfig attachmeRunConfig, Project project) {
    this.project = project;
    this.runConf = attachmeRunConfig;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    AttachmeServer.Console logger = new AttachmeServer.Console() {
      @Override
      public void info(String str) {
        procHandler.notifyTextAvailable(str + System.lineSeparator(), ProcessOutputType.STDOUT);
      }

      @Override
      public void error(String str) {
        procHandler.notifyTextAvailable(str + System.lineSeparator(), ProcessOutputType.STDERR);
      }
    };
    Thread thread = AttachmeServer.makeThread(runConf.getPort(), this, logger);
    this.procHandler = new MProcHandler(thread);
    ConsoleViewImpl console = new ConsoleViewImpl(this.project, false);
    console.attachToProcess(this.procHandler);
    thread.start();
    try {
      new AttachmeInstaller(logger).verifyInstallation();
    } catch (IOException e) {
      throw new ExecutionException("Could not install attachme files", e);
    }
    return new DefaultExecutionResult(console, this.procHandler);
  }

  public static long delay = 0;

  @Override
  public void onDebuggeeProcess(ProcessRegisterMsg msg, String debuggeeAddress) {
    if (msg.getPorts().isEmpty()) {
      procHandler.notifyTextAvailable("Received message with no ports", ProcessOutputType.STDERR);
      return;
    }
    if (delay == 0) {
      String home = System.getenv("HOME");
      System.out.println("home = " + (home));
      try {
        List<String> strings = Files.readAllLines(Path.of(home + "/.attachme/ATTACHME_DELAY"));
        System.out.println("strings = " + (strings));
        delay = Long.parseLong(strings.getFirst());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    AttachmeServer.lastConsole.info("current attach delay: " +delay);
    new Timer("").schedule(new TimerTask() {
      @Override
      public void run() {
        RemoteConnection config = new RemoteConnection(true, debuggeeAddress, msg.getPorts().get(0) + "", false);
        AttachmeDebugger.attach(project, config, msg.getPid());
      }
    }, delay);
  }

  @Override
  public void onFinished() {
    this.procHandler.shutdown();
  }

  static class MProcHandler extends ProcessHandler {

    final Thread t;

    MProcHandler(Thread t) {
      this.t = t;
    }

    @Override
    protected void destroyProcessImpl() {
      if (!t.isInterrupted())
        t.interrupt();
    }

    @Override
    protected void detachProcessImpl() {
      destroyProcessImpl();
    }

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return null;
    }

    public void shutdown() {
      super.notifyProcessTerminated(0);
    }
  }
}
