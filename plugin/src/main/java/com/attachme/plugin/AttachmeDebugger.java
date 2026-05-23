package com.attachme.plugin;

import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Alarm;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AttachmeDebugger {

  private AttachmeDebugger() {
  }

  public static void attach(Project project, RemoteConnection con, Integer pid) {
    RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project).createConfiguration("Attachme pid owner: " + pid, ProcessAttachRunConfigurationType.FACTORY);
    runSettings.setActivateToolWindowBeforeRun(false);
    runSettings.setFocusToolWindowBeforeRun(false);
    ((ProcessAttachRunConfiguration) runSettings.getConfiguration()).connection = con;
    RunContentDescriptor selected = ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
    ProgramRunnerUtil.executeConfiguration(runSettings, new ProcessAttachDebugExecutor());
    restoreToolWindow(project, ToolWindowId.RUN, 200);
  }
  private static void restoreToolWindow(Project project, String toolWindowId, int count) {
    if (count <= 0 || project.isDisposed() || toolWindowId == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
      if (toolWindow != null) {
        toolWindow.activate(null, false);
      }
      Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
      alarm.addRequest(() -> restoreToolWindow(project, toolWindowId, count - 1), 10);
    }, ModalityState.nonModal());
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
