// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.actions.RecentLocationsAction.RecentLocationItem;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import static com.intellij.ide.actions.RecentLocationsAction.EMPTY_FILE_TEXT;

class RecentLocationsRenderer extends ColoredListCellRenderer<RecentLocationItem> {
  private static final JBColor BACKGROUND_COLOR = JBColor.namedColor("Table.lightSelectionBackground", new JBColor(0xE9EEF5, 0x464A4D));

  @NotNull private final Project myProject;
  @NotNull private final SpeedSearch mySpeedSearch;
  @NotNull private final Ref<Map<IdeDocumentHistoryImpl.PlaceInfo, String>> myBreadcrumbsMap;

  RecentLocationsRenderer(@NotNull Project project,
                          @NotNull SpeedSearch speedSearch,
                          @NotNull Ref<Map<IdeDocumentHistoryImpl.PlaceInfo, String>> breadcrumbsMap) {
    myProject = project;
    mySpeedSearch = speedSearch;
    myBreadcrumbsMap = breadcrumbsMap;
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends RecentLocationItem> list,
                                                RecentLocationItem value,
                                                int index,
                                                boolean selected,
                                                boolean hasFocus) {
    EditorEx editor = value.getEditor();
    if (myProject.isDisposed() || editor.isDisposed()) {
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    Color defaultBackground = editor.getColorsScheme().getDefaultBackground();
    String breadcrumbs = myBreadcrumbsMap.get().get(value.getInfo());
    JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
    panel.add(createTitleComponent(myProject, list, mySpeedSearch, breadcrumbs, value.getInfo(), defaultBackground, selected, index));

    String text = editor.getDocument().getText();
    if (!StringUtil.isEmpty(text)) {
      panel.add(setupEditorComponent(editor, text, mySpeedSearch, selected ? BACKGROUND_COLOR : defaultBackground));
    }

    return panel;
  }

  @NotNull
  private static JComponent createTitleComponent(@NotNull Project project,
                                                 @NotNull JList<? extends RecentLocationItem> list,
                                                 @NotNull SpeedSearch speedSearch,
                                                 @Nullable String breadcrumb,
                                                 @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo,
                                                 @NotNull Color background,
                                                 boolean selected,
                                                 int index) {
    JComponent title = JBUI.Panels
      .simplePanel()
      .withBorder(JBUI.Borders.empty())
      .addToLeft(createTitleTextComponent(project, list, speedSearch, placeInfo, breadcrumb, selected))
      .addToCenter(createTitledSeparator(background));

    title.setBorder(BorderFactory.createEmptyBorder(index == 0 ? 5 : 15, 8, 6, 0));
    title.setBackground(background);

    return title;
  }

  @NotNull
  private static TitledSeparator createTitledSeparator(@NotNull Color background) {
    TitledSeparator titledSeparator = new TitledSeparator();
    titledSeparator.setBackground(background);
    return titledSeparator;
  }

  @NotNull
  private static JComponent setupEditorComponent(@NotNull EditorEx editor,
                                                 @NotNull String text,
                                                 @NotNull SpeedSearch speedSearch,
                                                 @NotNull Color backgroundColor) {
    Iterable<TextRange> ranges = speedSearch.matchingFragments(text);
    if (ranges != null) {
      selectSearchResultsInEditor(editor, ranges.iterator());
    }
    else {
      RecentLocationsAction.clearSelectionInEditor(editor);
    }

    editor.setBackgroundColor(backgroundColor);
    editor.setBorder(JBUI.Borders.emptyLeft(5));

    if (EMPTY_FILE_TEXT.equals(editor.getDocument().getText())) {
      editor.getMarkupModel().addRangeHighlighter(0,
                                                  EMPTY_FILE_TEXT.length(),
                                                  HighlighterLayer.SYNTAX,
                                                  createEmptyTextForegroundTextAttributes(),
                                                  HighlighterTargetArea.EXACT_RANGE);
    }

    return editor.getComponent();
  }

  @NotNull
  private static SimpleColoredComponent createTitleTextComponent(@NotNull Project project,
                                                                 @NotNull JList<? extends RecentLocationItem> list,
                                                                 @NotNull SpeedSearch speedSearch,
                                                                 @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo,
                                                                 @Nullable String breadcrumbText,
                                                                 boolean selected) {
    SimpleColoredComponent titleTextComponent = new SimpleColoredComponent();

    String fileName = placeInfo.getFile().getName();
    String text = fileName;
    titleTextComponent.append(fileName, SimpleTextAttributes.fromTextAttributes(createLabelForegroundTextAttributes()));

    if (StringUtil.isNotEmpty(breadcrumbText) && !StringUtil.equals(breadcrumbText, fileName)) {
      text += " " + breadcrumbText;
      titleTextComponent.append("  ");
      titleTextComponent.append(breadcrumbText, SimpleTextAttributes.fromTextAttributes(createBreadcrumbsTextAttributes()));
    }

    Icon icon = fetchIcon(project, placeInfo);

    if (icon != null) {
      titleTextComponent.setIcon(icon);
      titleTextComponent.setIconTextGap(4);
    }

    titleTextComponent.setBorder(JBUI.Borders.empty());

    if (speedSearch.matchingFragments(text) != null) {
      SpeedSearchUtil.applySpeedSearchHighlighting(list, titleTextComponent, false, selected);
    }

    return titleTextComponent;
  }

  @Nullable
  private static Icon fetchIcon(@NotNull Project project, @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    Icon icon = null;
    PsiFile file = PsiManager.getInstance(project).findFile(placeInfo.getFile());
    if (file != null) {
      ItemPresentation presentation = file.getPresentation();
      if (presentation != null) {
        icon = presentation.getIcon(false);
      }
    }

    if (icon == null) {
      Language language = LanguageUtil.getFileLanguage(placeInfo.getFile());
      if (language != null) {
        final LanguageFileType fileType = language.getAssociatedFileType();
        if (fileType != null) {
          icon = fileType.getIcon();
        }
      }
    }

    return icon;
  }

  @NotNull
  private static TextAttributes createLabelForegroundTextAttributes() {
    TextAttributes textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes();
    textAttributes.setFontType(Font.BOLD);
    textAttributes.setForegroundColor(UIUtil.getLabelTextForeground());
    return textAttributes;
  }

  @NotNull
  private static TextAttributes createBreadcrumbsTextAttributes() {
    TextAttributes textAttributes = SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES.toTextAttributes();
    textAttributes.setFontType(Font.BOLD);
    return textAttributes;
  }

  @NotNull
  private static TextAttributes createEmptyTextForegroundTextAttributes() {
    TextAttributes textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes();
    textAttributes.setForegroundColor(UIUtil.getLabelDisabledForeground());
    return textAttributes;
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends RecentLocationItem> list,
                                       RecentLocationItem value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
  }

  private static void selectSearchResultsInEditor(@NotNull Editor editor, @NotNull Iterator<TextRange> resultIterator) {
    if (!editor.getCaretModel().supportsMultipleCarets()) {
      return;
    }
    ArrayList<CaretState> caretStates = new ArrayList<>();
    while (resultIterator.hasNext()) {
      TextRange findResult = resultIterator.next();

      int caretOffset = findResult.getEndOffset();

      int selectionStartOffset = findResult.getStartOffset();
      int selectionEndOffset = findResult.getEndOffset();
      EditorActionUtil.makePositionVisible(editor, caretOffset);
      EditorActionUtil.makePositionVisible(editor, selectionStartOffset);
      EditorActionUtil.makePositionVisible(editor, selectionEndOffset);
      caretStates.add(new CaretState(editor.offsetToLogicalPosition(caretOffset),
                                     editor.offsetToLogicalPosition(selectionStartOffset),
                                     editor.offsetToLogicalPosition(selectionEndOffset)));
    }
    if (caretStates.isEmpty()) {
      return;
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }
}
