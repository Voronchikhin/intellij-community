// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.removePathTail;

public class CreateBranchOrTagDialog extends DialogWrapper {
  @NotNull private final File mySrcFile;
  @NotNull private final Url mySrcURL;
  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;

  private TextFieldWithBrowseButton myToURLText;

  private JTextArea myCommentText;
  private JPanel myTopPanel;
  private JRadioButton myWorkingCopyRadioButton;
  private JRadioButton myRepositoryRadioButton;
  private TextFieldWithBrowseButton myWorkingCopyField;
  private TextFieldWithBrowseButton myRepositoryField;
  private SvnRevisionPanel myRevisionPanel;
  private ComboboxWithBrowseButton myBranchTagBaseComboBox;
  private JTextField myBranchTextField;
  private JRadioButton myBranchOrTagRadioButton;
  private JRadioButton myAnyLocationRadioButton;
  private JButton myProjectButton;
  private JLabel myErrorLabel;
  private JLabel myUseThisVariantToLabel;
  private JBCheckBox mySwitchOnCreate;

  @NonNls private static final String HELP_ID = "vcs.subversion.branch";
  private SvnBranchConfigurationNew myBranchConfiguration;
  private final VirtualFile mySrcVirtualFile;
  private final Url myWcRootUrl;

  public CreateBranchOrTagDialog(@NotNull SvnVcs vcs, @NotNull File file) throws VcsException {
    super(vcs.getProject(), true);
    mySrcFile = file;
    myVcs = vcs;
    myProject = vcs.getProject();
    setResizable(true);
    setTitle(message("dialog.title.branch"));
    myUseThisVariantToLabel.setBorder(JBUI.Borders.emptyBottom(10));
    myProjectButton.setIcon(AllIcons.Nodes.IdeaProject);
    myBranchTagBaseComboBox.setPreferredSize(new Dimension(myBranchTagBaseComboBox.getPreferredSize().width,
                                                           myWorkingCopyField.getPreferredSize().height));

    Info info = myVcs.getInfo(file);
    if (info == null || info.getURL() == null) {
      throw new VcsException("Can not find url for file: " + file.getPath());
    }
    mySrcURL = info.getURL();

    myWorkingCopyField.addBrowseFolderListener("Select Working Copy Location", "Select Location to Copy From:",
                                               myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myWorkingCopyField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateControls();
      }
    });
    myRepositoryField.addActionListener(e -> {
      Url url = SelectLocationDialog.selectLocation(myProject, mySrcURL);
      if (url != null) {
        myRepositoryField.setText(url.toString());
      }
    });
    myRepositoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateToURL();
      }
    });
    myToURLText.addActionListener(e -> {
      try {
        Url url = createUrl(myToURLText.getText());
        String dstName = mySrcURL.getTail();
        Url destination = SelectLocationDialog
          .selectCopyDestination(myProject, removePathTail(url), message("label.copy.select.location.dialog.copy.as"), dstName, false);

        if (destination != null) {
          myToURLText.setText(destination.toString());
        }
      }
      catch (SvnBindException ex) {
        showErrorDialog(myProject, ex.getMessage(), message("dialog.title.select.repository.location"));
      }
    });

    RootUrlInfo root = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(file);
    if (root == null) {
      throw new VcsException("Can not find working copy for file: " + file.getPath());
    }
    mySrcVirtualFile = root.getVirtualFile();
    myWcRootUrl = root.getUrl();

    myRevisionPanel.setRoot(mySrcVirtualFile);
    myRevisionPanel.setProject(myProject);
    myRevisionPanel.setUrlProvider(() -> mySrcURL);
    myRevisionPanel.setRevisionText(String.valueOf(info.getRevision()));
    updateBranchTagBases();

    myRevisionPanel.addChangeListener(e -> getOKAction().setEnabled(isOKActionEnabled()));

    init();
    ActionListener listener = e -> updateControls();
    myWorkingCopyRadioButton.addActionListener(listener);
    myRepositoryRadioButton.addActionListener(listener);
    myBranchOrTagRadioButton.addActionListener(listener);
    myAnyLocationRadioButton.addActionListener(listener);
    updateControls();
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateToURL();
      }
    });
    updateToURL();
    myProjectButton.addActionListener(e -> myRepositoryField.setText(myWcRootUrl.toString()));
    myBranchTagBaseComboBox.addActionListener(e -> {
      BranchConfigurationDialog.configureBranches(myProject, mySrcVirtualFile);
      updateBranchTagBases();
      updateControls();
    });
    myBranchTagBaseComboBox.getComboBox().addActionListener(e -> {
      updateToURL();
      updateControls();
    });
  }

  private void updateBranchTagBases() {
    myBranchConfiguration = SvnBranchConfigurationManager.getInstance(myProject).get(mySrcVirtualFile);
    final String[] strings = ArrayUtil.toStringArray(myBranchConfiguration.getBranchUrls());
    myBranchTagBaseComboBox.getComboBox().setModel(new DefaultComboBoxModel(strings));
  }

  private void updateToURL() {
    if (myBranchConfiguration == null) {
      return;
    }
    String relativeUrl;
    if (myWorkingCopyRadioButton.isSelected()) {
      relativeUrl = myBranchConfiguration.getRelativeUrl(mySrcURL.toString());
    }
    else {
      relativeUrl = myBranchConfiguration.getRelativeUrl(myRepositoryField.getText());
    }

    final Object selectedBranch = myBranchTagBaseComboBox.getComboBox().getSelectedItem();
    if (relativeUrl != null && selectedBranch != null) {
      myToURLText.setText(selectedBranch.toString() + "/" + myBranchTextField.getText() + relativeUrl);
    }
  }

  private String getToURLTextFromBranch() {
    final Object selectedBranch = myBranchTagBaseComboBox.getComboBox().getSelectedItem();
    if (selectedBranch != null) {
      return selectedBranch + "/" + myBranchTextField.getText();
    }
    return null;
  }

  private void updateControls() {
    myWorkingCopyField.setEnabled(myWorkingCopyRadioButton.isSelected());
    mySwitchOnCreate.setEnabled(myWorkingCopyRadioButton.isSelected());
    myRepositoryField.setEnabled(myRepositoryRadioButton.isSelected());
    myRevisionPanel.setEnabled(myRepositoryRadioButton.isSelected());
    myProjectButton.setEnabled(myRepositoryRadioButton.isSelected());

    myBranchTagBaseComboBox.setEnabled(myBranchOrTagRadioButton.isSelected());
    myBranchTextField.setEnabled(myBranchOrTagRadioButton.isSelected());
    myToURLText.setEnabled(myAnyLocationRadioButton.isSelected());
    myUseThisVariantToLabel.setForeground(myWorkingCopyRadioButton.isSelected() ? UIUtil.getActiveTextColor() : UIUtil.getInactiveTextColor());

    getOKAction().setEnabled(isOKActionEnabled());
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return HELP_ID;
  }

  protected void init() {
    super.init();
    myWorkingCopyField.setText(mySrcFile.toString());
    myRepositoryField.setText(mySrcURL.toString());
    myToURLText.setText(mySrcURL.toString());
    updateControls();

    myWorkingCopyRadioButton.setSelected(true);
  }

  public String getComment() {
    return myCommentText.getText();
  }

  public Revision getRevision() {
    if (myWorkingCopyRadioButton.isSelected()) {
      return Revision.WORKING;
    }
    else {
      try {
        return myRevisionPanel.getRevision();
      }
      catch (ConfigurationException e) {
        return Revision.UNDEFINED;
      }
    }
  }

  public String getToURL() {
    if (myBranchOrTagRadioButton.isSelected()) {
      return getToURLTextFromBranch();
    }
    return myToURLText.getText();
  }

  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myToURLText;
  }

  protected String getDimensionServiceKey() {
    return "svn.copyDialog";
  }

  public boolean isOKActionEnabled() {
    myErrorLabel.setText(" ");
    if (myBranchOrTagRadioButton.isSelected() && myBranchTagBaseComboBox.getComboBox().getSelectedItem() == null) {
      myErrorLabel.setText(message("create.branch.no.base.location.error"));
      return false;
    }
    String url = getToURL();
    if (url != null && url.trim().length() > 0) {
      if (myRepositoryRadioButton.isSelected()) {
        Revision revision = getRevision();
        if (!revision.isValid() || revision.isLocal()) {
          myErrorLabel.setText(message("create.branch.invalid.revision.error", myRevisionPanel.getRevisionText()));
          return false;
        }
        return true;
      }
      else if (myWorkingCopyRadioButton.isSelected()) {
        return true;
      }
    }
    return false;
  }

  public boolean isCopyFromWorkingCopy() {
    return myWorkingCopyRadioButton.isSelected();
  }

  public String getCopyFromPath() {
    return myWorkingCopyField.getText();
  }

  public String getCopyFromUrl() {
    return myRepositoryField.getText();
  }

  public boolean isSwitchOnCreate() {
    return mySwitchOnCreate.isSelected();
  }
}
