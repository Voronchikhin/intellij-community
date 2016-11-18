/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter;
import org.jetbrains.plugins.gradle.model.GradleExtensions;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 11/16/2016
 */
@State(name = "GradleExtensions", storages = @Storage("gradle_extensions.xml"))
public class GradleExtensionsSettings implements PersistentStateComponent<GradleExtensionsSettings.Settings> {
  private final Settings myState = new Settings();

  public GradleExtensionsSettings(Project project) {
    ExternalSystemApiUtil.subscribe(project, GradleConstants.SYSTEM_ID, new GradleSettingsListenerAdapter() {
      @Override
      public void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
        myState.remove(linkedProjectPaths);
      }
    });
  }

  @Nullable
  @Override
  public Settings getState() {
    return myState;
  }

  @Override
  public void loadState(Settings state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  @NotNull
  public static Settings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleExtensionsSettings.class).myState;
  }

  public static class Settings {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "project", keyAttributeName = "path")
    @NotNull
    public Map<String, GradleProject> projects = new HashMap<>();

    public void add(@NotNull String rootPath, @NotNull Map<String, GradleExtensions> extensions) {
      GradleProject gradleProject = new GradleProject();
      for (Map.Entry<String, GradleExtensions> entry : extensions.entrySet()) {
        GradleExtensionsData extensionsData = new GradleExtensionsData();
        for (org.jetbrains.plugins.gradle.model.GradleExtension extension : entry.getValue().list()) {
          GradleExtension gradleExtension = new GradleExtension();
          gradleExtension.name = extension.getName();
          gradleExtension.rootTypeFqn = extension.getRootTypeFqn();
          gradleExtension.namedObjectTypeFqn = extension.getNamedObjectTypeFqn();
          extensionsData.extensions.add(gradleExtension);
        }
        gradleProject.extensions.put(entry.getKey(), extensionsData);
      }

      Map<String, GradleProject> projects = new HashMap<>(this.projects);
      projects.put(rootPath, gradleProject);
      this.projects = projects;
    }

    public void remove(Set<String> rootPaths) {
      Map<String, GradleProject> projects = new HashMap<>(this.projects);
      for (String path : rootPaths) {
        projects.remove(path);
      }
      this.projects = projects;
    }

    @Nullable
    public GradleExtensionsData getExtensionsFor(@Nullable Module module) {
      if (module == null) return null;
      String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (rootProjectPath == null) return null;
      String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      if (projectPath == null) return null;
      GradleProject gradleProject = projects.get(rootProjectPath);
      if (gradleProject == null) return null;
      return gradleProject.extensions.get(projectPath);
    }
  }

  @Tag("sub-project")
  static class GradleProject {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "project", keyAttributeName = "path")
    @NotNull
    public Map<String, GradleExtensionsData> extensions = new HashMap<>();
  }


  @Tag("extensions")
  public static class GradleExtensionsData {
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public List<GradleExtension> extensions = new SmartList<>();
  }

  @Tag("ext")
  public static class GradleExtension {
    @Attribute("name")
    public String name;
    @Attribute("rootTypeFqn")
    public String rootTypeFqn;
    @Attribute("namedObjectTypeFqn")
    public String namedObjectTypeFqn;
  }
}
