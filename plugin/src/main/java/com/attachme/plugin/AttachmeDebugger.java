package com.attachme.plugin;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSessionStartedResult;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

public class AttachmeDebugger {

  private AttachmeDebugger() {
  }

  public static void attach(Project project, RemoteConnection con, Integer pid) {
    RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project).createConfiguration("Attachme pid owner: " + pid, ProcessAttachRunConfigurationType.FACTORY);
    runSettings.setActivateToolWindowBeforeRun(false);
    runSettings.setFocusToolWindowBeforeRun(false);
    ProcessAttachRunConfiguration conf = (ProcessAttachRunConfiguration) runSettings.getConfiguration();
    conf.connection = con;
    conf.setShowConsoleOnStdOut(false);
    conf.setShowConsoleOnStdErr(false);
    ProgramRunnerUtil.executeConfiguration(runSettings, new ProcessAttachDebugExecutor());
  }

  public static class ProcessAttachDebugExecutor extends DefaultDebugExecutor {
    @NotNull
    @Override
    public String getId() {
      return "ProcessAttachDebugExecutor";
    }
  }

  public static class ProcessAttachDebuggerRunner extends GenericDebuggerRunner {
    @NotNull
    @Override
    public String getRunnerId() {
      return "ProcessAttachDebuggerRunner";
    }

    @Nullable
    @Override
    protected RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
      throws ExecutionException {
      return attachVirtualMachine(state, environment, ((RemoteState) state).getRemoteConnection(), false);
    }

    @Nullable
    @Override
    protected RunContentDescriptor attachVirtualMachine(RunProfileState state,
                                                        ExecutionEnvironment env,
                                                        RemoteConnection connection,
                                                        long pollTimeout) throws ExecutionException {
      DebugEnvironment environment = new DefaultDebugEnvironment(env, state, connection, pollTimeout);
      DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(env.getProject()).attachVirtualMachine(environment);
      if (debuggerSession == null) {
        return null;
      }

      AtomicReference<ExecutionException> ex = new AtomicReference<>();
      AtomicReference<RunContentDescriptor> result = new AtomicReference<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          DebugProcessImpl debugProcess = debuggerSession.getProcess();
          XDebugProcessStarter starter = new XDebugProcessStarter() {
            @Override
            @NotNull
            public XDebugProcess start(@NotNull XDebugSession session) {
              XDebugSessionImpl sessionImpl = (XDebugSessionImpl) session;
              ExecutionResult executionResult = debugProcess.getExecutionResult();
              sessionImpl.addExtraActions(executionResult.getActions());
              if (executionResult instanceof DefaultExecutionResult) {
                sessionImpl.addRestartActions(((DefaultExecutionResult) executionResult).getRestartActions());
              }
              sessionImpl.setPauseActionSupported(true);
              return JavaDebugProcess.create(session, debuggerSession);
            }
          };
          String sessionName = env.getRunProfile() != null ? env.getRunProfile().getName() : "Attachme";
          if (sessionName == null) {
            sessionName = "Attachme";
          }
          XSessionStartedResult sessionStartedResult = XDebuggerManager.getInstance(env.getProject())
            .newSessionBuilder(starter)
            .sessionName(sessionName)
            .environment(env)
            .showTab(true)
            .showToolWindowOnSuspendOnly(true)
            .startSession();
          RunContentDescriptor descriptor = sessionStartedResult.getRunContentDescriptor();
          if (descriptor != null) {
            descriptor.setActivateToolWindowWhenAdded(false);
            descriptor.setAutoFocusContent(false);
          }
          result.set(descriptor);
        } catch (ExecutionException e) {
          ex.set(e);
        }
      }, ModalityState.any());

      if (ex.get() != null) {
        throw ex.get();
      }
      return result.get();
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
      return executorId.equals("ProcessAttachDebugExecutor");
    }
  }

  public static class ProcessAttachRunConfiguration extends RunConfigurationBase<Element> {
    RemoteConnection connection;

    protected ProcessAttachRunConfiguration(@NotNull Project project) {
      super(project, ProcessAttachRunConfigurationType.FACTORY, "ProcessAttachRunConfiguration");
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      throw new IllegalStateException("Editing is not supported");
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
      if (connection == null) throw new NullPointerException();
      return new RemoteStateState(getProject(), connection);
    }
  }

  public static final class ProcessAttachRunConfigurationType implements ConfigurationType {
    static final ProcessAttachRunConfigurationType INSTANCE = new ProcessAttachRunConfigurationType();

    static final ConfigurationFactory FACTORY = new ConfigurationFactory(INSTANCE) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new ProcessAttachRunConfiguration(project);
      }
    };

    @NotNull
    @Nls
    @Override
    public String getDisplayName() {
      return getId();
    }

    @Nls
    @Override
    public String getConfigurationTypeDescription() {
      return getId();
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @NotNull
    @Override
    public String getId() {
      return "ProcessAttachRunConfigurationType";
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
      return new ConfigurationFactory[]{FACTORY};
    }

    @Override
    public String getHelpTopic() {
      return "reference.dialogs.rundebug.ProcessAttachRunConfigurationType";
    }
  }
}
