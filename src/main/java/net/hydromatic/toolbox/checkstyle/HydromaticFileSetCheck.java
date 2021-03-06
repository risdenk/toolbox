/*
 * Licensed to Julian Hyde under one or more contributor license
 * agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership. Julian Hyde
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.hydromatic.toolbox.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractFileSetCheck;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checker that applies some custom checks to each file.
 */
public class HydromaticFileSetCheck extends AbstractFileSetCheck {

  private static final Pattern PATTERN1 = Pattern.compile(".*\\\\n\" \\+ .*");
  private static final Pattern PATTERN2 = Pattern.compile("^\\{@link .*\\}$");
  private static final Pattern PATTERN3 = Pattern.compile(".*@param +[^ ]+ *$");
  private static final Pattern PATTERN4 = Pattern.compile(".* href=.*CALCITE-.*");
  private static final Pattern PATTERN5 = Pattern.compile(
      ".*<a href=\"https://issues.apache.org/jira/browse/CALCITE-[0-9]+\">\\[CALCITE-[0-9]+\\].*");

  boolean isProto(File file) {
    return file.getAbsolutePath().contains("/proto/")
        || file.getName().endsWith("Base64.java");
  }

  static <E> E last(List<E> list) {
    return list.get(list.size() - 1);
  }

  void afterFile(File file, List<String> lines) {
    if (file.getName().endsWith(".java")) {
      String b = file.getName().replaceAll(".*/", "");
      final String line = "// End " + b;
      if (!last(lines).equals(line) && !isProto(file)) {
        log(lines.size(), "Last line should be ''{0}''", line);
      }
    }
  }

  protected void processFiltered(File file, List<String> list) {
    boolean off = false;
    int endCount = 0;
    int maxLineLength = 80;
    final String path = file.getAbsolutePath()
        .replace('\\', '/'); // for windows
    if (path.contains("/calcite/")) {
      maxLineLength = 100;
    }
    int i = 0;
    for (String line : list) {
      ++i;
      if (line.contains("\t")) {
        log(i, "Tab");
      }
      if (line.contains("CHECKSTYLE: ON")) {
        off = false;
      }
      if (line.contains("CHECKSTYLE: OFF")) {
        off = true;
      }
      if (off) {
        continue;
      }
      if (line.startsWith("// End ")) {
        if (endCount++ > 0) {
          log(i, "End seen more than once");
        }
      }
      if (isMatches(PATTERN1, line)) {
        log(i, "Newline in string should be at end of line");
      }
      if (line.contains("{@link")) {
        if (!line.contains("}")) {
          log(i, "Split @link");
        }
      }
      if (line.endsWith("@Override")) {
        log(i, "@Override should not be on its own line");
      }
      if (line.endsWith("<p>") && !isProto(file)) {
        log(i, "Orphan <p>. Make it the first line of a paragraph");
      }
      if (line.contains("@")
          && !line.contains("@see")
          && line.length() > maxLineLength) {
        String s = line
            .replaceAll("^ *\\* *", "")
            .replaceAll(" \\*/$", "")
            .replaceAll("[;.,]$", "")
            .replaceAll("<li>", "");
        if (!isMatches(PATTERN2, s)
            && !file.getName().endsWith("CalciteResource.java")) {
          log(i, "Javadoc line too long ({0} chars)", line.length());
        }
      }
      if (isMatches(PATTERN3, line)) {
        log(i, "Parameter with no description");
      }
      if (isMatches(PATTERN4, line) && !isMatches(PATTERN5, line)) {
        log(i, "Bad JIRA reference");
      }
      if (file.getName().endsWith(".java")
          && (line.contains("(") || line.contains(")"))) {
        String s = deString(line);
        int o = 0;
        for (int j = 0; j < s.length(); j++) {
          char c = s.charAt(j);
          if (c == '('
              && j > 0
              && Character.isJavaIdentifierPart(s.charAt(j - 1))) {
            ++o;
          } else if (c == ')') {
            --o;
          }
        }
        if (o > 1) {
          log(i, "Open parentheses exceed closes by 2 or more");
        }
      }
    }
    afterFile(file, list);
  }

  private boolean isMatches(Pattern pattern, String line) {
    return pattern.matcher(line).matches();
  }

  static String deString(String line) {
    if (!line.contains("\"")) {
      return line;
    }
    final StringBuilder b = new StringBuilder();
    int i = 0;
  outer:
    for (;;) {
      int j = line.indexOf('"', i);
      if (j < 0) {
        b.append(line, i, line.length());
        return b.toString();
      }
      b.append(line, i, j);
      for (int k = j + 1;;) {
        if (k >= line.length()) {
          b.append("string");
          i = line.length();
          continue outer;
        }
        char c = line.charAt(k++);
        switch (c) {
        case '\\':
          k++;
          break;
        case '"':
          b.append("string");
          i = k;
          continue outer;
        }
      }
    }
  }

  public void fireErrors2(File fileName) {
    fireErrors(fileName.getAbsolutePath());
  }
}

// End HydromaticFileSetCheck.java
