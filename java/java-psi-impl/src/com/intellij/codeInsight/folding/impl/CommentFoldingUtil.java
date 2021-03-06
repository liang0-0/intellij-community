// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

public final class CommentFoldingUtil {

  /**
   * Construct descriptor for comment folding.
   *
   * @param comment            comment to fold
   * @param document           document with comment
   * @param isCollapse         is comment collapsed by default or not
   * @param processedComments  already processed comments
   * @param isCustomRegionFunc determines whether element contains custom region tag
   */
  @Nullable
  public static NamedFoldingDescriptor getCommentDescriptor(@NotNull PsiComment comment,
                                                            @NotNull Document document,
                                                            @NotNull Set<PsiElement> processedComments,
                                                            @NotNull Predicate<PsiElement> isCustomRegionFunc,
                                                            boolean isCollapse) {
    if (!processedComments.add(comment)) return null;

    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(comment.getLanguage());
    if (!(commenter instanceof CodeDocumentationAwareCommenter)) return null;

    final CodeDocumentationAwareCommenter docCommenter = (CodeDocumentationAwareCommenter)commenter;
    final IElementType commentType = comment.getTokenType();

    final TextRange commentRange = getCommentRange(comment, processedComments, isCustomRegionFunc, docCommenter);
    if (commentRange == null) return null;

    final String placeholder = getCommentPlaceholder(document, commentType, commentRange);
    if (placeholder == null) return null;

    return new NamedFoldingDescriptor(comment.getNode(), commentRange, null, placeholder, isCollapse, Collections.emptySet());
  }

  @Nullable
  private static TextRange getCommentRange(@NotNull PsiComment comment,
                                           @NotNull Set<PsiElement> processedComments,
                                           @NotNull Predicate<PsiElement> isCustomRegionFunc,
                                           @NotNull CodeDocumentationAwareCommenter docCommenter) {
    final IElementType commentType = comment.getTokenType();
    if (commentType == docCommenter.getDocumentationCommentTokenType() || commentType == docCommenter.getBlockCommentTokenType()) {
      return comment.getTextRange();
    }

    if (commentType != docCommenter.getLineCommentTokenType()) return null;

    return getOneLineCommentRange(comment, processedComments, isCustomRegionFunc, docCommenter);
  }

  /**
   * We want to allow to fold subsequent single line comments like
   * <pre>
   *     // this is comment line 1
   *     // this is comment line 2
   * </pre>
   *
   * @param startComment      comment to check
   * @param processedComments set that contains already processed elements. It is necessary because we process all elements of
   *                          the PSI tree, hence, this method may be called for both comments from the example above. However,
   *                          we want to create fold region during the first comment processing, put second comment to it and
   *                          skip processing when current method is called for the second element
   */
  @Nullable
  private static TextRange getOneLineCommentRange(@NotNull PsiComment startComment,
                                                  @NotNull Set<PsiElement> processedComments,
                                                  @NotNull Predicate<PsiElement> isCustomRegionFunc,
                                                  @NotNull CodeDocumentationAwareCommenter docCommenter) {
    if (isCustomRegionFunc.test(startComment)) return null;

    PsiElement end = null;
    for (PsiElement current = startComment.getNextSibling(); current != null; current = current.getNextSibling()) {
      ASTNode node = current.getNode();
      if (node == null) {
        break;
      }
      final IElementType elementType = node.getElementType();
      if (elementType == docCommenter.getLineCommentTokenType() &&
          !isCustomRegionFunc.test(current) &&
          !processedComments.contains(current)) {
        end = current;
        // We don't want to process, say, the second comment in case of three subsequent comments when it's being examined
        // during all elements traversal. I.e. we expect to start from the first comment and grab as many subsequent
        // comments as possible during the single iteration.
        processedComments.add(current);
        continue;
      }
      if (elementType == TokenType.WHITE_SPACE) {
        continue;
      }
      break;
    }

    if (end == null) return null;

    return new TextRange(startComment.getTextRange().getStartOffset(), end.getTextRange().getEndOffset());
  }

  /**
   * Construct placeholder for comment based on its type.
   *
   * @param document     document with comment
   * @param commentType  type of comment
   * @param commentRange text range of comment
   */
  @Nullable
  public static String getCommentPlaceholder(@NotNull Document document,
                                             @NotNull IElementType commentType,
                                             @NotNull TextRange commentRange) {
    return getCommentPlaceholder(document, commentType, commentRange, "...");
  }


  /**
   * Construct placeholder for comment based on its type.
   *
   * @param document     document with comment
   * @param commentType  type of comment
   * @param commentRange text range of comment
   * @param replacement  replacement for comment content. included in placeholder
   */
  @Nullable
  public static String getCommentPlaceholder(@NotNull Document document,
                                             @NotNull IElementType commentType,
                                             @NotNull TextRange commentRange,
                                             @NotNull String replacement) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(commentType.getLanguage());
    if (!(commenter instanceof CodeDocumentationAwareCommenter)) return null;

    final CodeDocumentationAwareCommenter docCommenter = (CodeDocumentationAwareCommenter)commenter;

    final String placeholder;
    if (commentType == docCommenter.getLineCommentTokenType()) {
      placeholder = getLineCommentPlaceholderText(commenter, replacement);
    }
    else if (commentType == docCommenter.getBlockCommentTokenType()) {
      placeholder = getMultilineCommentPlaceholderText(commenter, replacement);
    }
    else if (commentType == docCommenter.getDocumentationCommentTokenType()) {
      placeholder = getDocCommentPlaceholderText(document, docCommenter, commentRange, replacement);
    }
    else {
      placeholder = null;
    }

    return placeholder;
  }

  @Nullable
  private static String getDocCommentPlaceholderText(@NotNull Document document,
                                                     @NotNull CodeDocumentationAwareCommenter commenter,
                                                     @NotNull TextRange commentRange,
                                                     @NotNull String replacement) {
    final String prefix = commenter.getDocumentationCommentPrefix();
    final String suffix = commenter.getDocumentationCommentSuffix();
    final String linePrefix = commenter.getDocumentationCommentLinePrefix();

    if (prefix == null || suffix == null || linePrefix == null) return null;

    final String header = getCommentHeader(document, suffix, linePrefix, commentRange);

    return getCommentPlaceholder(prefix, suffix, header, replacement);
  }

  @Nullable
  private static String getMultilineCommentPlaceholderText(@NotNull Commenter commenter, @NotNull String replacement) {
    final String prefix = commenter.getBlockCommentPrefix();
    final String suffix = commenter.getBlockCommentSuffix();

    if (prefix == null || suffix == null) return null;

    return getCommentPlaceholder(prefix, suffix, null, replacement);
  }

  @Nullable
  private static String getLineCommentPlaceholderText(@NotNull Commenter commenter, @NotNull String replacement) {
    final String prefix = commenter.getLineCommentPrefix();

    if (prefix == null) return null;

    return getCommentPlaceholder(prefix, null, null, replacement);
  }

  /**
   * Construct comment placeholder based on rule placeholder ::= prefix[text ]replacement[suffix] .
   *
   * @param text        part of comment content to include in placeholder
   * @param replacement replacement for the rest of comment content
   */
  @NotNull
  public static String getCommentPlaceholder(@NotNull String prefix,
                                             @Nullable String suffix,
                                             @Nullable String text,
                                             @NotNull String replacement) {
    final StringBuilder sb = new StringBuilder();
    sb.append(prefix);

    if (text != null && text.length() > 0) {
      sb.append(text);
      sb.append(" ");
    }

    sb.append(replacement);

    if (suffix != null) sb.append(suffix);

    return sb.toString();
  }

  /**
   * Get second line from comment excluding comment suffix and comment line prefix.
   *
   * @param document      document with comment
   * @param commentSuffix doc comment suffix
   * @param linePrefix    prefix for doc comment line
   * @param commentRange  comment text range in document
   */
  @NotNull
  public static String getCommentHeader(@NotNull Document document,
                                        @NotNull String commentSuffix,
                                        @NotNull String linePrefix,
                                        @NotNull TextRange commentRange) {
    final int nFirstCommentLine = document.getLineNumber(commentRange.getStartOffset());
    final int nSecondCommentLine = nFirstCommentLine + 1;

    if (nSecondCommentLine >= document.getLineCount()) return "";

    final int endOffset = document.getLineEndOffset(nSecondCommentLine);
    if (endOffset > commentRange.getEndOffset()) return "";

    final int startOffset = document.getLineStartOffset(nSecondCommentLine);

    String line = document.getText(new TextRange(startOffset, endOffset));
    line = line.trim();

    line = StringUtil.trimEnd(line, commentSuffix);
    line = StringUtil.trimStart(line, linePrefix);

    return line;
  }
}
