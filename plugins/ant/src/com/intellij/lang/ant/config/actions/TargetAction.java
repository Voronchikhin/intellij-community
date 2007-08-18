package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;

public final class TargetAction extends AnAction {
  public static final String DEFAULT_TARGET_NAME = AntBundle.message("ant.target.name.default.target");

  private final String myBuildName;
  private final String[] myTargets;
  private final String myDebugString;
  
  public TargetAction(final AntBuildFile buildFile, final String displayName, final String[] targets, final String description) {
    Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(displayName, false);
    templatePresentation.setDescription(description);
    myBuildName = buildFile.getPresentableName();
    myTargets = targets;
    myDebugString = "Target action: " + displayName+ "; Build: " + buildFile.getPresentableName() + "; Project: " + buildFile.getProject().getLocation();
  }

  public String toString() {
    return myDebugString;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    for (final AntBuildFile buildFile : AntConfiguration.getInstance(project).getBuildFiles()) {
      final String name = buildFile.getPresentableName();
      if (name != null && myBuildName.equals(name)) {
        String[] targets = myTargets.length == 1 && DEFAULT_TARGET_NAME.equals(myTargets[0]) ? ArrayUtil.EMPTY_STRING_ARRAY : myTargets;
        ExecutionHandler.runBuild((AntBuildFileBase)buildFile, targets, null, dataContext, AntBuildListener.NULL);
        return;
      }
    }
  }
}